#!/usr/bin/env python3
"""S-expression pretty-printer for SMT-LIB2 (and generic Lisp) files.

Reads source from stdin, writes formatted output to stdout.
Wired into VSCode via the "Custom Local Formatters" extension
(jkillian.custom-local-formatters); see .vscode/settings.json.

Expressions whose flat form fits in WIDTH columns stay on one line;
longer ones break with 2-space indentation. Comments, string literals
("" escapes) and |quoted symbols| are preserved verbatim.
"""
import sys

WIDTH = 100
INDENT = 2

LPAREN, RPAREN, ATOM, COMMENT = range(4)


def tokenize(src):
    tokens = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c in " \t\r\n":
            i += 1
        elif c == ";":
            j = src.find("\n", i)
            j = n if j == -1 else j
            tokens.append((COMMENT, src[i:j]))
            i = j
        elif c == "(":
            tokens.append((LPAREN, "("))
            i += 1
        elif c == ")":
            tokens.append((RPAREN, ")"))
            i += 1
        elif c == '"':
            j = i + 1
            while j < n:
                if src[j] == '"':
                    if j + 1 < n and src[j + 1] == '"':  # "" escape
                        j += 2
                    else:
                        break
                else:
                    j += 1
            tokens.append((ATOM, src[i : j + 1]))
            i = j + 1
        elif c == "|":
            j = src.find("|", i + 1)
            j = n - 1 if j == -1 else j
            tokens.append((ATOM, src[i : j + 1]))
            i = j + 1
        else:
            j = i
            while j < n and src[j] not in ' \t\r\n();"|':
                j += 1
            tokens.append((ATOM, src[i:j]))
            i = j
    return tokens


def parse(tokens):
    """Return a list of top-level nodes: strings (atoms/comments) or lists."""
    top, stack = [], []
    for kind, text in tokens:
        if kind == LPAREN:
            node = []
            (stack[-1] if stack else top).append(node)
            stack.append(node)
        elif kind == RPAREN:
            if stack:
                stack.pop()
        elif kind == COMMENT:
            # keep comments only at top level; inside expressions they are
            # rare in generated files and would complicate layout
            (stack[-1] if stack else top).append(("comment", text))
        else:
            (stack[-1] if stack else top).append(text)
    return top


def flat(node):
    if isinstance(node, str):
        return node
    if isinstance(node, tuple):  # comment
        return node[1]
    return "(" + " ".join(flat(c) for c in node) + ")"


def render(node, col, out):
    if isinstance(node, str):
        out.append(node)
        return
    if isinstance(node, tuple):
        out.append(node[1])
        return
    text = flat(node)
    if col + len(text) <= WIDTH or "\n" in text:
        out.append(text)
        return
    out.append("(")
    inner = col + INDENT
    first = True
    for child in node:
        if first:
            render(child, col + 1, out)
            first = False
        else:
            out.append("\n" + " " * inner)
            render(child, inner, out)
    out.append(")")


def main():
    src = sys.stdin.read()
    nodes = parse(tokenize(src))
    pieces = []
    for node in nodes:
        out = []
        render(node, 0, out)
        pieces.append("".join(out))
    result = "\n".join(pieces) + "\n"
    # safety: never emit output whose token stream differs from the input
    if [t for t in tokenize(src) if t[0] != COMMENT] != [
        t for t in tokenize(result) if t[0] != COMMENT
    ]:
        sys.stdout.write(src)
        return
    sys.stdout.write(result)


if __name__ == "__main__":
    main()
