package tasks

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestClient_List(t *testing.T) {
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/todos" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		_ = json.NewEncoder(w).Encode([]Task{{ID: 1, Title: "demo", Completed: true}})
	}))
	t.Cleanup(api.Close)

	client := NewClient(api.URL, api.Client())
	tasks, err := client.List()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(tasks) != 1 || tasks[0].Title != "demo" || !tasks[0].Completed {
		t.Fatalf("unexpected tasks: %+v", tasks)
	}
}

func TestClient_CreateValidation(t *testing.T) {
	client := NewClient("http://example.com", nil)
	if _, err := client.Create("   "); err != ErrTitleRequired {
		t.Fatalf("expected ErrTitleRequired, got %v", err)
	}
}

func TestClient_UpdateAndDelete(t *testing.T) {
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodPut && r.URL.Path == "/todos/5":
			var body map[string]any
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				t.Fatalf("invalid json: %v", err)
			}
			if body["title"] != "updated" || body["completed"] != true {
				t.Fatalf("unexpected payload: %#v", body)
			}
			_ = json.NewEncoder(w).Encode(Task{ID: 5, Title: "updated", Completed: true})
		case r.Method == http.MethodDelete && r.URL.Path == "/todos/5":
			w.WriteHeader(http.StatusNoContent)
		default:
			t.Fatalf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
	}))
	t.Cleanup(api.Close)

	client := NewClient(api.URL, api.Client())

	updated, err := client.Update(5, "updated", true)
	if err != nil {
		t.Fatalf("update returned error: %v", err)
	}
	if updated.ID != 5 || updated.Title != "updated" || !updated.Completed {
		t.Fatalf("unexpected task: %+v", updated)
	}

	if err := client.Delete(5); err != nil {
		t.Fatalf("delete returned error: %v", err)
	}
}

func TestClient_DeleteNotFound(t *testing.T) {
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	t.Cleanup(api.Close)

	client := NewClient(api.URL, api.Client())
	if err := client.Delete(42); err != ErrNotFound {
		t.Fatalf("expected ErrNotFound, got %v", err)
	}
}
