package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"github.com/mikeyphw/dustui/go/internal/host"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	os.Exit(host.RunAlias(ctx, "dust-space", os.Args[1:], host.Streams{In: os.Stdin, Out: os.Stdout, Err: os.Stderr}))
}
