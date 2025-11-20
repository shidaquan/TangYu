package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

// Item represents a simple record stored by the service.
type Item struct {
	ID          string    `json:"id"`
	Name        string    `json:"name"`
	Description string    `json:"description"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

// Store keeps items in memory and guards concurrent access.
type Store struct {
	mu    sync.RWMutex
	items map[string]Item
}

// NewStore builds an empty Store instance.
func NewStore() *Store {
	return &Store{items: make(map[string]Item)}
}

func newID() string {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return fmt.Sprintf("%d", time.Now().UTC().UnixNano())
	}
	return hex.EncodeToString(buf)
}

// CreateItem adds a new item to the store.
func (s *Store) CreateItem(name, description string) Item {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now().UTC()
	item := Item{
		ID:          newID(),
		Name:        name,
		Description: description,
		CreatedAt:   now,
		UpdatedAt:   now,
	}
	s.items[item.ID] = item
	return item
}

// GetItem fetches an item by ID.
func (s *Store) GetItem(id string) (Item, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	item, ok := s.items[id]
	return item, ok
}

// ListItems returns all stored items.
func (s *Store) ListItems() []Item {
	s.mu.RLock()
	defer s.mu.RUnlock()

	result := make([]Item, 0, len(s.items))
	for _, item := range s.items {
		result = append(result, item)
	}
	return result
}

// UpdateItem updates the fields of an item.
func (s *Store) UpdateItem(id, name, description string) (Item, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	item, ok := s.items[id]
	if !ok {
		return Item{}, fmt.Errorf("item %s not found", id)
	}

	item.Name = name
	item.Description = description
	item.UpdatedAt = time.Now().UTC()
	s.items[id] = item
	return item, nil
}

// DeleteItem removes an item by ID.
func (s *Store) DeleteItem(id string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	if _, ok := s.items[id]; ok {
		delete(s.items, id)
		return true
	}
	return false
}

// apiQuote fetches a short message from a remote API.
func apiQuote(ctx context.Context, client *http.Client, url string) (string, error) {
	if client == nil {
		return "", errors.New("client is required")
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Accept", "text/plain")

	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", fmt.Errorf("remote API returned %s", resp.Status)
	}

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	text := strings.TrimSpace(string(data))
	if text == "" {
		return "", errors.New("empty response from API")
	}
	return text, nil
}

// respondJSON writes JSON responses and handles errors.
func respondJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if payload != nil {
		_ = json.NewEncoder(w).Encode(payload)
	}
}

func handleItems(store *Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			respondJSON(w, http.StatusOK, store.ListItems())
		case http.MethodPost:
			var input struct {
				Name        string `json:"name"`
				Description string `json:"description"`
			}
			if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
				respondJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
				return
			}
			if strings.TrimSpace(input.Name) == "" {
				respondJSON(w, http.StatusBadRequest, map[string]string{"error": "name is required"})
				return
			}
			item := store.CreateItem(input.Name, input.Description)
			respondJSON(w, http.StatusCreated, item)
		default:
			respondJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		}
	}
}

func handleItemByID(store *Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		id := strings.TrimPrefix(r.URL.Path, "/items/")
		if id == "" {
			respondJSON(w, http.StatusBadRequest, map[string]string{"error": "missing item ID"})
			return
		}

		switch r.Method {
		case http.MethodGet:
			item, ok := store.GetItem(id)
			if !ok {
				respondJSON(w, http.StatusNotFound, map[string]string{"error": "item not found"})
				return
			}
			respondJSON(w, http.StatusOK, item)
		case http.MethodPut:
			var input struct {
				Name        string `json:"name"`
				Description string `json:"description"`
			}
			if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
				respondJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
				return
			}
			if strings.TrimSpace(input.Name) == "" {
				respondJSON(w, http.StatusBadRequest, map[string]string{"error": "name is required"})
				return
			}
			item, err := store.UpdateItem(id, input.Name, input.Description)
			if err != nil {
				respondJSON(w, http.StatusNotFound, map[string]string{"error": err.Error()})
				return
			}
			respondJSON(w, http.StatusOK, item)
		case http.MethodDelete:
			if !store.DeleteItem(id) {
				respondJSON(w, http.StatusNotFound, map[string]string{"error": "item not found"})
				return
			}
			respondJSON(w, http.StatusNoContent, nil)
		default:
			respondJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		}
	}
}

func handleQuote(client *http.Client, apiURL string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			quote, err := apiQuote(r.Context(), client, apiURL)
			if err != nil {
				log.Printf("api error: %v", err)
				respondJSON(w, http.StatusBadGateway, map[string]string{"error": "failed to fetch quote"})
				return
			}
			respondJSON(w, http.StatusOK, map[string]string{"quote": quote})
		default:
			respondJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		}
	}
}

func setupServer(store *Store, client *http.Client) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/items", handleItems(store))
	mux.HandleFunc("/items/", handleItemByID(store))
	mux.HandleFunc("/api/quote", handleQuote(client, "https://api.github.com/zen"))
	mux.HandleFunc("/health", func(w http.ResponseWriter, _ *http.Request) {
		respondJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	return mux
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	store := NewStore()
	client := &http.Client{Timeout: 5 * time.Second}
	srv := &http.Server{
		Addr:         ":" + port,
		Handler:      setupServer(store, client),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}

	log.Printf("Starting CRUD API on :%s", port)
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("server error: %v", err)
	}
}
