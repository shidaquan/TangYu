package httpserver

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"tangyu/internal/tasks"
)

func TestHandler_CRUD(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/todos":
			_ = json.NewEncoder(w).Encode([]tasks.Task{{ID: 1, Title: "task", Completed: false}})
		case r.Method == http.MethodPost && r.URL.Path == "/todos":
			_ = json.NewEncoder(w).Encode(tasks.Task{ID: 2, Title: "created", Completed: false})
		case r.Method == http.MethodPut && r.URL.Path == "/todos/2":
			_ = json.NewEncoder(w).Encode(tasks.Task{ID: 2, Title: "updated", Completed: true})
		case r.Method == http.MethodDelete && r.URL.Path == "/todos/2":
			w.WriteHeader(http.StatusNoContent)
		default:
			t.Fatalf("unexpected upstream request: %s %s", r.Method, r.URL.Path)
		}
	}))
	t.Cleanup(upstream.Close)

	srv := New(tasks.NewClient(upstream.URL, upstream.Client()))
	handler := srv.Handler()

	t.Run("list", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/tasks", nil)
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("unexpected status: %d", rec.Code)
		}
	})

	t.Run("create", func(t *testing.T) {
		payload := bytes.NewBufferString(`{"title":"created"}`)
		req := httptest.NewRequest(http.MethodPost, "/tasks", payload)
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusCreated {
			t.Fatalf("unexpected status: %d", rec.Code)
		}
	})

	t.Run("update", func(t *testing.T) {
		payload := bytes.NewBufferString(`{"title":"updated","completed":true}`)
		req := httptest.NewRequest(http.MethodPut, "/tasks/2", payload)
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("unexpected status: %d", rec.Code)
		}
	})

	t.Run("delete", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodDelete, "/tasks/2", nil)
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("unexpected status: %d", rec.Code)
		}
	})
}

func TestHandler_UpdateRequiresCompleted(t *testing.T) {
	srv := New(tasks.NewClient("http://example.com", &http.Client{}))
	handler := srv.Handler()

	req := httptest.NewRequest(http.MethodPut, "/tasks/1", bytes.NewBufferString(`{"title":"x"}`))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected bad request, got %d", rec.Code)
	}
}
