package main

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestStoreCRUD(t *testing.T) {
	store := NewStore()

	created := store.CreateItem("alpha", "first")
	if created.ID == "" {
		t.Fatal("expected ID to be set")
	}

	if item, ok := store.GetItem(created.ID); !ok || item.Name != "alpha" {
		t.Fatalf("unexpected item lookup: %#v (ok=%v)", item, ok)
	}

	list := store.ListItems()
	if len(list) != 1 {
		t.Fatalf("expected 1 item, got %d", len(list))
	}

	updated, err := store.UpdateItem(created.ID, "beta", "second")
	if err != nil {
		t.Fatalf("update failed: %v", err)
	}
	if updated.Name != "beta" || updated.Description != "second" {
		t.Fatalf("unexpected updated item: %#v", updated)
	}

	if !store.DeleteItem(created.ID) {
		t.Fatal("expected delete to succeed")
	}
	if _, ok := store.GetItem(created.ID); ok {
		t.Fatal("expected item to be removed")
	}
}

func TestHandlersCRUDFlow(t *testing.T) {
	store := NewStore()
	client := &http.Client{Timeout: 2 * time.Second}
	srv := httptest.NewServer(setupServer(store, client))
	defer srv.Close()

	// Create an item.
	payload := map[string]string{"name": "demo", "description": "test item"}
	body, _ := json.Marshal(payload)
	res, err := http.Post(srv.URL+"/items", "application/json", bytes.NewReader(body))
	if err != nil {
		t.Fatalf("post failed: %v", err)
	}
	if res.StatusCode != http.StatusCreated {
		t.Fatalf("expected status %d, got %d", http.StatusCreated, res.StatusCode)
	}
	var created Item
	if err := json.NewDecoder(res.Body).Decode(&created); err != nil {
		t.Fatalf("decode failed: %v", err)
	}
	_ = res.Body.Close()

	// Fetch by ID.
	res, err = http.Get(srv.URL + "/items/" + created.ID)
	if err != nil {
		t.Fatalf("get by id failed: %v", err)
	}
	if res.StatusCode != http.StatusOK {
		t.Fatalf("expected status %d, got %d", http.StatusOK, res.StatusCode)
	}
	_ = res.Body.Close()

	// Update item.
	updatePayload := map[string]string{"name": "updated", "description": "changed"}
	body, _ = json.Marshal(updatePayload)
	req, _ := http.NewRequest(http.MethodPut, srv.URL+"/items/"+created.ID, bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	res, err = http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("put failed: %v", err)
	}
	if res.StatusCode != http.StatusOK {
		t.Fatalf("expected status %d, got %d", http.StatusOK, res.StatusCode)
	}
	_ = res.Body.Close()

	// Delete item.
	req, _ = http.NewRequest(http.MethodDelete, srv.URL+"/items/"+created.ID, nil)
	res, err = http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("delete failed: %v", err)
	}
	if res.StatusCode != http.StatusNoContent {
		t.Fatalf("expected status %d, got %d", http.StatusNoContent, res.StatusCode)
	}
	_ = res.Body.Close()

	// Ensure item is gone.
	res, err = http.Get(srv.URL + "/items/" + created.ID)
	if err != nil {
		t.Fatalf("get after delete failed: %v", err)
	}
	if res.StatusCode != http.StatusNotFound {
		t.Fatalf("expected status %d, got %d", http.StatusNotFound, res.StatusCode)
	}
	_ = res.Body.Close()
}

func TestHandleQuoteUsesInjectedURL(t *testing.T) {
	quotes := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("test quote"))
	}))
	defer quotes.Close()

	handler := handleQuote(http.DefaultClient, quotes.URL)

	req := httptest.NewRequest(http.MethodGet, "/api/quote", nil)
	rr := httptest.NewRecorder()
	handler(rr, req)

	if rr.Code != http.StatusOK {
		t.Fatalf("expected status %d, got %d", http.StatusOK, rr.Code)
	}
	var payload map[string]string
	if err := json.Unmarshal(rr.Body.Bytes(), &payload); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if payload["quote"] != "test quote" {
		t.Fatalf("unexpected payload: %#v", payload)
	}
}

func TestAPIQuoteErrors(t *testing.T) {
	client := &http.Client{Timeout: time.Second}
	ctx := context.Background()

	// Server returning error status.
	failing := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "boom", http.StatusTeapot)
	}))
	defer failing.Close()

	if _, err := apiQuote(ctx, client, failing.URL); err == nil {
		t.Fatal("expected error for non-2xx status")
	}
}
