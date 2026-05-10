package tests

import "testing"

func TestPlaceholder(t *testing.T) {
	if testing.Short() {
		t.Skip("placeholder")
	}
}
