package platformcontract

import (
	"go/ast"
	"go/parser"
	"go/token"
	"path/filepath"
	"strings"
	"testing"
)

func TestSQLiteAuthorityDoesNotReenterDBBeforeRowsClose(t *testing.T) {
	root := repoRoot(t)
	paths, err := filepath.Glob(filepath.Join(root, "go", "internal", "state", "*.go"))
	if err != nil {
		t.Fatal(err)
	}
	files := token.NewFileSet()
	for _, path := range paths {
		if strings.HasSuffix(path, "_test.go") {
			continue
		}
		parsed, err := parser.ParseFile(files, path, nil, 0)
		if err != nil {
			t.Fatalf("parse %s: %v", path, err)
		}
		ast.Inspect(parsed, func(node ast.Node) bool {
			fn, ok := node.(*ast.FuncDecl)
			if !ok || fn.Body == nil {
				return true
			}
			type rowsQuery struct {
				name string
				call token.Pos
				pos  token.Pos
			}
			var outer []rowsQuery
			ast.Inspect(fn.Body, func(n ast.Node) bool {
				assign, ok := n.(*ast.AssignStmt)
				if !ok {
					return true
				}
				for i, rhs := range assign.Rhs {
					call, ok := rhs.(*ast.CallExpr)
					if !ok || !isDirectSQLiteDBQuery(call) || i >= len(assign.Lhs) {
						continue
					}
					if id, ok := assign.Lhs[i].(*ast.Ident); ok {
						outer = append(outer, rowsQuery{name: id.Name, call: call.Pos(), pos: assign.Pos()})
					}
				}
				return true
			})

			for _, query := range outer {
				closePos := fn.Body.End()
				ast.Inspect(fn.Body, func(n ast.Node) bool {
					call, ok := n.(*ast.CallExpr)
					if !ok || call.Pos() <= query.pos || call.Pos() >= closePos {
						return true
					}
					selector, ok := call.Fun.(*ast.SelectorExpr)
					if !ok || selector.Sel.Name != "Close" {
						return true
					}
					if id, ok := selector.X.(*ast.Ident); ok && id.Name == query.name {
						closePos = call.Pos()
					}
					return true
				})

				ast.Inspect(fn.Body, func(n ast.Node) bool {
					call, ok := n.(*ast.CallExpr)
					if !ok || call.Pos() == query.call || call.Pos() <= query.pos || call.Pos() >= closePos {
						return true
					}
					if isDirectSQLiteDBQuery(call) {
						t.Errorf(
							"%s: %s opens Rows %q at %s then directly queries SQLite again at %s before explicit Close; single-connection authority forbids DB re-entry",
							filepath.Base(path), fn.Name.Name, query.name, files.Position(query.pos), files.Position(call.Pos()),
						)
					}
					return true
				})
			}
			return false
		})
	}
}

func isDirectSQLiteDBQuery(call *ast.CallExpr) bool {
	selector, ok := call.Fun.(*ast.SelectorExpr)
	if !ok || (selector.Sel.Name != "Query" && selector.Sel.Name != "QueryContext") {
		return false
	}
	receiver, ok := selector.X.(*ast.SelectorExpr)
	return ok && receiver.Sel.Name == "db"
}
