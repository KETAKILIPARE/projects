import pytest
from app.services.scanner import FileScanner

JAVA_FILENAME        = "UserService.java"
PYTHON_FILENAME      = "utils.py"
LANGUAGE_JAVA        = "java"
LANGUAGE_PYTHON      = "python"
NONEXISTENT_PATH     = "/nonexistent/path/that/does/not/exist"
JAVA_CONTENT         = "public class UserService {\n    public User findById(Long id) {\n        return repository.findById(id);\n    }\n}\n"
PYTHON_CONTENT       = "def calculate_hash(data: str) -> str:\n    import hashlib\n    return hashlib.sha256(data.encode()).hexdigest()\n"
IOERROR_CONTENT      = "unreadable"


@pytest.fixture
def temp_project(tmp_path):
    java_dir = tmp_path / "src" / "main" / "java"
    java_dir.mkdir(parents=True)
    (java_dir / JAVA_FILENAME).write_text(JAVA_CONTENT)
    (tmp_path / PYTHON_FILENAME).write_text(PYTHON_CONTENT)

    (tmp_path / "node_modules" / "lodash").mkdir(parents=True)
    (tmp_path / "node_modules" / "lodash" / "index.js").write_text("module.exports = {};")

    (tmp_path / "target" / "classes").mkdir(parents=True)
    (tmp_path / "target" / "classes" / "UserService.class").write_text("binary")

    (tmp_path / "__pycache__").mkdir()
    (tmp_path / "__pycache__" / "utils.cpython-311.pyc").write_text("bytecode")

    (tmp_path / "venv" / "lib").mkdir(parents=True)
    (tmp_path / "venv" / "lib" / "helper.py").write_text("pass")

    (tmp_path / ".git").mkdir()
    (tmp_path / ".git" / "config").write_text("[core]")

    (tmp_path / "README.md").write_text("# Project")

    return tmp_path


class TestFileScanner:

    scanner = FileScanner()

    def test_scan_finds_java_files(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path for f in files]
        assert any(JAVA_FILENAME in p for p in paths), \
            "scanner should find Java source files"

    def test_scan_finds_python_files(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path for f in files]
        assert any(PYTHON_FILENAME in p for p in paths), \
            "scanner should find Python source files"

    def test_scan_returns_correct_language_for_java(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        java_file = next(f for f in files if JAVA_FILENAME in f.file_path)
        assert java_file.language == LANGUAGE_JAVA, \
            "Java files should be assigned language 'java'"

    def test_scan_returns_correct_language_for_python(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        python_file = next(f for f in files if PYTHON_FILENAME in f.file_path)
        assert python_file.language == LANGUAGE_PYTHON, \
            "Python files should be assigned language 'python'"

    def test_scan_returns_file_content(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        java_file = next(f for f in files if JAVA_FILENAME in f.file_path)
        assert "UserService" in java_file.content, \
            "scanned file content should contain the class name"

    def test_scan_ignores_node_modules(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path.replace("\\", "/") for f in files]
        assert not any("/node_modules/" in p for p in paths), \
            "scanner should ignore node_modules directory"

    def test_scan_ignores_target_directory(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path.replace("\\", "/") for f in files]
        assert not any("/target/" in p for p in paths), \
            "scanner should ignore target directory"

    def test_scan_ignores_pycache_directory(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path.replace("\\", "/") for f in files]
        assert not any("__pycache__" in p for p in paths), \
            "scanner should ignore __pycache__ directory"

    def test_scan_ignores_venv_directory(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path.replace("\\", "/") for f in files]
        assert not any("/venv/" in p for p in paths), \
            "scanner should ignore venv directory"

    def test_scan_ignores_git_directory(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path.replace("\\", "/") for f in files]
        assert not any("/.git/" in p for p in paths), \
            "scanner should ignore .git directory"

    def test_scan_ignores_unsupported_file_types(self, temp_project):
        files = self.scanner.scan(str(temp_project))
        paths = [f.file_path for f in files]
        assert not any("README.md" in p for p in paths), \
            "scanner should ignore unsupported file types like .md"

    def test_scan_raises_for_nonexistent_folder(self):
        with pytest.raises(ValueError, match="does not exist"):
            self.scanner.scan(NONEXISTENT_PATH)

    def test_scan_returns_empty_list_for_empty_folder(self, tmp_path):
        files = self.scanner.scan(str(tmp_path))
        assert files == [], \
            "scanning an empty folder should return an empty list"
