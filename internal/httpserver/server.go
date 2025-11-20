package httpserver

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"

	"tangyu/internal/tasks"
)

type Server struct {
	client *tasks.Client
}

func New(client *tasks.Client) *Server {
	return &Server{client: client}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})

	mux.HandleFunc("GET /tasks", func(w http.ResponseWriter, _ *http.Request) {
		tasks, err := s.client.List()
		if err != nil {
			writeError(w, http.StatusBadGateway, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, tasks)
	})

	mux.HandleFunc("POST /tasks", func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Title string `json:"title"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeError(w, http.StatusBadRequest, fmt.Sprintf("invalid json: %v", err))
			return
		}

		task, err := s.client.Create(body.Title)
		if err != nil {
			status := http.StatusBadGateway
			if errors.Is(err, tasks.ErrTitleRequired) {
				status = http.StatusBadRequest
			}
			writeError(w, status, err.Error())
			return
		}

		writeJSON(w, http.StatusCreated, task)
	})

	mux.HandleFunc("PUT /tasks/{id}", func(w http.ResponseWriter, r *http.Request) {
		id, err := parseID(r.PathValue("id"))
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}

		var body struct {
			Title     string `json:"title"`
			Completed *bool  `json:"completed"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeError(w, http.StatusBadRequest, fmt.Sprintf("invalid json: %v", err))
			return
		}
		if body.Completed == nil {
			writeError(w, http.StatusBadRequest, "completed is required")
			return
		}

		task, err := s.client.Update(id, body.Title, *body.Completed)
		if err != nil {
			status := http.StatusBadGateway
			switch {
			case errors.Is(err, tasks.ErrTitleRequired):
				status = http.StatusBadRequest
			case errors.Is(err, tasks.ErrNotFound):
				status = http.StatusNotFound
			}
			writeError(w, status, err.Error())
			return
		}

		writeJSON(w, http.StatusOK, task)
	})

	mux.HandleFunc("DELETE /tasks/{id}", func(w http.ResponseWriter, r *http.Request) {
		id, err := parseID(r.PathValue("id"))
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}

		if err := s.client.Delete(id); err != nil {
			status := http.StatusBadGateway
			if errors.Is(err, tasks.ErrNotFound) {
				status = http.StatusNotFound
			}
			writeError(w, status, err.Error())
			return
		}

		writeJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
	})

	return mux
}

func parseID(raw string) (int, error) {
	id, err := strconv.Atoi(raw)
	if err != nil || id < 1 {
		return 0, fmt.Errorf("invalid task id: %q", raw)
	}
	return id, nil
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
