package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

const (
	maxSpecBytes    = 50 * 1024
	execTimeout     = 8 * time.Second
	defaultPort     = "8080"
	binaryEnv       = "SPEC_TO_REST_BIN"
	defaultBinPath  = "/usr/local/bin/spec-to-rest"
	corsAllowOrigin = "CORS_ALLOW_ORIGIN"
)

type compileRequest struct {
	Spec   string `json:"spec"`
	Target string `json:"target"`
}

type compileResponse struct {
	OK     bool   `json:"ok"`
	Stdout string `json:"stdout"`
	Stderr string `json:"stderr"`
	Error  string `json:"error,omitempty"`
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = defaultPort
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("/api/compile", handleCompile)

	srv := &http.Server{
		Addr:              ":" + port,
		Handler:           withCORS(mux),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       30 * time.Second,
	}

	log.Printf("playground server listening on :%s", port)
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("server: %v", err)
	}
}

func withCORS(next http.Handler) http.Handler {
	allow := os.Getenv(corsAllowOrigin)
	if allow == "" {
		allow = "*"
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", allow)
		w.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		w.Header().Set("Access-Control-Max-Age", "600")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func handleCompile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST only", http.StatusMethodNotAllowed)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxSpecBytes+2048)
	var req compileRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, compileResponse{Error: "invalid JSON body: " + err.Error()})
		return
	}
	if len(req.Spec) == 0 {
		writeJSON(w, http.StatusBadRequest, compileResponse{Error: "empty spec"})
		return
	}
	if len(req.Spec) > maxSpecBytes {
		writeJSON(w, http.StatusRequestEntityTooLarge,
			compileResponse{Error: fmt.Sprintf("spec exceeds %d bytes", maxSpecBytes)})
		return
	}

	args, err := argsFor(req.Target)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, compileResponse{Error: err.Error()})
		return
	}

	tmpDir, err := os.MkdirTemp("", "spec-")
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, compileResponse{Error: "tmp: " + err.Error()})
		return
	}
	defer os.RemoveAll(tmpDir)

	specPath := filepath.Join(tmpDir, "playground.spec")
	if err := os.WriteFile(specPath, []byte(req.Spec), 0o600); err != nil {
		writeJSON(w, http.StatusInternalServerError, compileResponse{Error: "write: " + err.Error()})
		return
	}

	stdout, stderr, runErr := runBinary(append(args, specPath))
	resp := compileResponse{
		OK:     runErr == nil,
		Stdout: stdout,
		Stderr: stderr,
	}
	if runErr != nil {
		resp.Error = runErr.Error()
	}
	writeJSON(w, http.StatusOK, resp)
}

func argsFor(target string) ([]string, error) {
	switch target {
	case "check":
		return []string{"check", "--quiet"}, nil
	case "summary":
		return []string{"inspect", "--format", "summary", "--quiet"}, nil
	case "ir":
		return []string{"inspect", "--format", "ir", "--quiet"}, nil
	case "dafny":
		return []string{"inspect", "--format", "dafny", "--quiet"}, nil
	default:
		return nil, fmt.Errorf("unsupported target %q; allowed: check, summary, ir, dafny", target)
	}
}

func runBinary(args []string) (string, string, error) {
	bin := os.Getenv(binaryEnv)
	if bin == "" {
		bin = defaultBinPath
	}
	ctx, cancel := context.WithTimeout(context.Background(), execTimeout)
	defer cancel()

	cmd := exec.CommandContext(ctx, bin, args...)
	cmd.Env = []string{"HOME=/tmp", "PATH=/usr/local/bin:/usr/bin:/bin", "NO_COLOR=1"}

	var outBuf, errBuf strings.Builder
	cmd.Stdout = &outBuf
	cmd.Stderr = &errBuf

	err := cmd.Run()
	if ctx.Err() == context.DeadlineExceeded {
		return outBuf.String(), errBuf.String(), fmt.Errorf("execution timed out after %s", execTimeout)
	}
	if err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			return outBuf.String(), errBuf.String(),
				fmt.Errorf("spec-to-rest exited with code %d", exitErr.ExitCode())
		}
		return outBuf.String(), errBuf.String(), err
	}
	return outBuf.String(), errBuf.String(), nil
}

func writeJSON(w http.ResponseWriter, status int, body compileResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
