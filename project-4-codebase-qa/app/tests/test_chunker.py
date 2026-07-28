import pytest
from app.services.chunker import CodeChunker

JAVA_FILE_PATH   = "UserService.java"
PYTHON_FILE_PATH = "utils.py"
JS_FILE_PATH     = "app.js"
UNKNOWN_FILE_PATH = "script.go"
LANGUAGE_JAVA    = "java"
LANGUAGE_PYTHON  = "python"
LANGUAGE_JS      = "javascript"
LANGUAGE_UNKNOWN = "go"

JAVA_CODE = """\
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User save(User user) {
        return repository.save(user);
    }
}
"""

PYTHON_CODE = """\
def calculate_hash(data: str) -> str:
    import hashlib
    return hashlib.sha256(data.encode()).hexdigest()


def validate_email(email: str) -> bool:
    return "@" in email and "." in email


class UserValidator:
    def validate(self, user: dict) -> bool:
        return validate_email(user.get("email", ""))
"""

JS_CODE = """\
export function authenticate(token) {
    return jwtUtil.verify(token);
}

const hashPassword = async (password) => {
    return bcrypt.hash(password, 10);
};
"""

LARGE_PYTHON_CODE = "\n".join(
    [f"def func_{i}():" + "\n" + "\n".join(f"    x_{j} = {j}" for j in range(10))
     for i in range(8)]
)


class TestCodeChunker:

    chunker = CodeChunker()

    def test_chunk_java_detects_class_boundary(self):
        chunks = self.chunker.chunk(JAVA_CODE, JAVA_FILE_PATH, LANGUAGE_JAVA)
        context_names = [c.context_name for c in chunks if c.context_name]
        assert any("UserService" in name for name in context_names), \
            "should detect UserService class boundary in Java code"

    def test_chunk_python_detects_function_boundary(self):
        chunks = self.chunker.chunk(PYTHON_CODE, PYTHON_FILE_PATH, LANGUAGE_PYTHON)
        context_names = [c.context_name for c in chunks if c.context_name]
        assert any("calculate_hash" in name for name in context_names), \
            "should detect calculate_hash function boundary in Python code"

    def test_chunk_javascript_detects_function_boundary(self):
        chunks = self.chunker.chunk(JS_CODE, JS_FILE_PATH, LANGUAGE_JS)
        context_names = [c.context_name for c in chunks if c.context_name]
        assert any("authenticate" in name for name in context_names), \
            "should detect authenticate function boundary in JavaScript code"

    def test_chunk_unsupported_language_falls_back_to_line_chunking(self):
        chunks = self.chunker.chunk(JAVA_CODE, UNKNOWN_FILE_PATH, LANGUAGE_UNKNOWN)
        assert len(chunks) >= 1, \
            "unsupported language should fall back to line-based chunking and still produce chunks"
        assert all(c.context_name is None for c in chunks), \
            "line-based chunks should have no context name for unsupported languages"

    def test_chunk_empty_content_returns_empty_list(self):
        chunks = self.chunker.chunk("", PYTHON_FILE_PATH, LANGUAGE_PYTHON)
        assert chunks == [], \
            "empty content should return an empty list"

    def test_chunk_ids_are_unique(self):
        chunks = self.chunker.chunk(JAVA_CODE, JAVA_FILE_PATH, LANGUAGE_JAVA)
        ids = [c.chunk_id for c in chunks]
        assert len(ids) == len(set(ids)), \
            "all chunk IDs should be unique within the same file"

    def test_chunk_fields_are_populated(self):
        chunks = self.chunker.chunk(JAVA_CODE, JAVA_FILE_PATH, LANGUAGE_JAVA)
        for chunk in chunks:
            assert chunk.chunk_id, "chunk_id must not be empty"
            assert chunk.file_path == JAVA_FILE_PATH, "file_path must match input"
            assert chunk.language == LANGUAGE_JAVA, "language must match input"
            assert chunk.start_line >= 1, "start_line must be at least 1"
            assert chunk.end_line >= chunk.start_line, "end_line must be >= start_line"
            assert chunk.content.strip(), "chunk content must not be blank"

    def test_chunk_large_file_splits_into_multiple_chunks(self):
        chunks = self.chunker.chunk(LARGE_PYTHON_CODE, PYTHON_FILE_PATH, LANGUAGE_PYTHON)
        assert len(chunks) > 1, \
            "large file should be split into multiple chunks"

    def test_chunk_content_does_not_exceed_max_lines(self):
        from app.services.chunker import MAX_CHUNK_LINES
        chunks = self.chunker.chunk(LARGE_PYTHON_CODE, PYTHON_FILE_PATH, LANGUAGE_PYTHON)
        for chunk in chunks:
            line_count = len(chunk.content.splitlines())
            assert line_count <= MAX_CHUNK_LINES + 1, \
                f"chunk should not exceed MAX_CHUNK_LINES but had {line_count} lines"
