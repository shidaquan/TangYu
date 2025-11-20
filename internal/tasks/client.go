package tasks

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

var (
	ErrNotFound      = errors.New("task not found")
	ErrTitleRequired = errors.New("title is required")
)

type Task struct {
	ID        int       `json:"id"`
	Title     string    `json:"title"`
	Completed bool      `json:"completed"`
	CreatedAt time.Time `json:"createdAt,omitempty"`
}

type Client struct {
	baseURL    string
	httpClient *http.Client
}

func NewClient(baseURL string, httpClient *http.Client) *Client {
	client := httpClient
	if client == nil {
		client = http.DefaultClient
	}

	trimmed := strings.TrimRight(baseURL, "/")
	return &Client{baseURL: trimmed, httpClient: client}
}

func (c *Client) List() ([]Task, error) {
	url := fmt.Sprintf("%s/todos", c.baseURL)
	resp, err := c.httpClient.Get(url)
	if err != nil {
		return nil, fmt.Errorf("list tasks: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("list tasks: unexpected status %d", resp.StatusCode)
	}

	var tasks []Task
	if err := json.NewDecoder(resp.Body).Decode(&tasks); err != nil {
		return nil, fmt.Errorf("list tasks: decode response: %w", err)
	}
	return tasks, nil
}

func (c *Client) Create(title string) (Task, error) {
	cleaned := strings.TrimSpace(title)
	if cleaned == "" {
		return Task{}, ErrTitleRequired
	}

	body := map[string]any{
		"title":     cleaned,
		"completed": false,
	}
	payload, _ := json.Marshal(body)

	url := fmt.Sprintf("%s/todos", c.baseURL)
	resp, err := c.httpClient.Post(url, "application/json", bytes.NewReader(payload))
	if err != nil {
		return Task{}, fmt.Errorf("create task: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusCreated && resp.StatusCode != http.StatusOK {
		return Task{}, fmt.Errorf("create task: unexpected status %d", resp.StatusCode)
	}

	var task Task
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		return Task{}, fmt.Errorf("create task: decode response: %w", err)
	}
	return task, nil
}

func (c *Client) Update(id int, title string, completed bool) (Task, error) {
	if id < 1 {
		return Task{}, fmt.Errorf("invalid task id: %d", id)
	}
	cleaned := strings.TrimSpace(title)
	if cleaned == "" {
		return Task{}, ErrTitleRequired
	}

	body := map[string]any{
		"title":     cleaned,
		"completed": completed,
	}
	payload, _ := json.Marshal(body)

	url := fmt.Sprintf("%s/todos/%d", c.baseURL, id)
	req, err := http.NewRequest(http.MethodPut, url, bytes.NewReader(payload))
	if err != nil {
		return Task{}, fmt.Errorf("update task: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return Task{}, fmt.Errorf("update task: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return Task{}, ErrNotFound
	}
	if resp.StatusCode != http.StatusOK {
		return Task{}, fmt.Errorf("update task: unexpected status %d", resp.StatusCode)
	}

	var task Task
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		return Task{}, fmt.Errorf("update task: decode response: %w", err)
	}
	return task, nil
}

func (c *Client) Delete(id int) error {
	if id < 1 {
		return fmt.Errorf("invalid task id: %d", id)
	}

	url := fmt.Sprintf("%s/todos/%d", c.baseURL, id)
	req, err := http.NewRequest(http.MethodDelete, url, nil)
	if err != nil {
		return fmt.Errorf("delete task: %w", err)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("delete task: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return ErrNotFound
	}
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
		return fmt.Errorf("delete task: unexpected status %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}
	return nil
}
