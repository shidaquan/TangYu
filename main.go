package main

import (
	"log"
	"net/http"
	"os"

	"tangyu/internal/httpserver"
	"tangyu/internal/tasks"
)

func main() {
	baseURL := os.Getenv("TASK_API_BASE")
	if baseURL == "" {
		baseURL = "https://jsonplaceholder.typicode.com"
	}

	client := tasks.NewClient(baseURL, http.DefaultClient)
	srv := httpserver.New(client)

	addr := ":8080"
	if fromEnv := os.Getenv("PORT"); fromEnv != "" {
		addr = ":" + fromEnv
	}

	log.Printf("starting TangYu proxy server on %s (upstream: %s)", addr, baseURL)
	if err := http.ListenAndServe(addr, srv.Handler()); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}
