import pytest
from unittest.mock import MagicMock, patch
from app.models.schemas import CodeChunk
from app.services.qa_service import QAService

QUESTION_JWT        = "How is JWT token generated?"
QUESTION_ANYTHING   = "anything"
FILE_AUTH           = "AuthService.java"
LANGUAGE_JAVA       = "java"
CONTENT_GEN_TOKEN   = "public String generateToken(String username) { return jwtUtil.generate(username); }"
CONTEXT_GEN_TOKEN   = "generateToken"
LLM_ANSWER          = "The JWT token is generated in AuthService.generateToken using jwtUtil."
TOP_K_3             = 3
TOP_K_5             = 5
CHUNK_COUNT_3       = 3
CHUNK_COUNT_0       = 0


def make_chunk():
    return CodeChunk(
        chunk_id="chunk_1",
        file_path=FILE_AUTH,
        language=LANGUAGE_JAVA,
        start_line=5,
        end_line=15,
        content=CONTENT_GEN_TOKEN,
        context_name=CONTEXT_GEN_TOKEN,
    )


@pytest.fixture
def mock_store_with_chunks():
    store = MagicMock()
    store.count.return_value = CHUNK_COUNT_3
    store.search.return_value = [make_chunk()]
    return store


@pytest.fixture
def mock_store_empty():
    store = MagicMock()
    store.count.return_value = CHUNK_COUNT_0
    store.search.return_value = []
    return store


@pytest.fixture
def mock_groq():
    with patch("app.services.qa_service.Groq") as mock:
        client = MagicMock()
        message = MagicMock()
        message.content = LLM_ANSWER
        client.chat.completions.create.return_value.choices = [MagicMock(message=message)]
        mock.return_value = client
        yield mock


class TestQAService:

    def test_query_returns_answer_and_sources(self, mock_store_with_chunks, mock_groq):
        service = QAService(vector_store=mock_store_with_chunks)
        response = service.query(QUESTION_JWT, top_k=TOP_K_3)
        assert response.answer, "response should contain a non-empty answer"
        assert len(response.sources) > 0, "response should contain at least one source"

    def test_query_sources_are_code_chunks(self, mock_store_with_chunks, mock_groq):
        service = QAService(vector_store=mock_store_with_chunks)
        response = service.query(QUESTION_JWT, top_k=TOP_K_3)
        assert all(isinstance(s, CodeChunk) for s in response.sources), \
            "all sources should be CodeChunk instances"

    def test_query_calls_vector_store_search_with_correct_args(self, mock_store_with_chunks, mock_groq):
        service = QAService(vector_store=mock_store_with_chunks)
        service.query(QUESTION_JWT, top_k=TOP_K_5)
        mock_store_with_chunks.search.assert_called_once_with(QUESTION_JWT, top_k=TOP_K_5), \
            "should call vector store search with the exact question and top_k"

    def test_query_passes_chunk_context_to_llm(self, mock_store_with_chunks, mock_groq):
        service = QAService(vector_store=mock_store_with_chunks)
        service.query(QUESTION_JWT, top_k=TOP_K_3)
        call_args = mock_groq.return_value.chat.completions.create.call_args
        prompt = call_args[1]["messages"][0]["content"]
        assert CONTEXT_GEN_TOKEN in prompt or FILE_AUTH in prompt, \
            "LLM prompt should contain chunk context including function name or file"

    def test_query_returns_not_indexed_message_when_store_empty(self, mock_store_empty, mock_groq):
        service = QAService(vector_store=mock_store_empty)
        response = service.query(QUESTION_ANYTHING, top_k=TOP_K_3)
        assert "no code" in response.answer.lower() or "not indexed" in response.answer.lower(), \
            "should return a message indicating nothing is indexed when store is empty"
        assert response.sources == [], \
            "sources should be empty when store is empty"

    def test_query_returns_not_found_message_when_search_returns_empty(self, mock_groq):
        store = MagicMock()
        store.count.return_value = CHUNK_COUNT_3
        store.search.return_value = []
        service = QAService(vector_store=store)
        response = service.query(QUESTION_ANYTHING, top_k=TOP_K_3)
        assert "couldn't find" in response.answer.lower() or "not find" in response.answer.lower(), \
            "should return a message indicating no relevant code found when search returns empty"
        assert response.sources == [], \
            "sources should be empty when search returns no results"

    def test_query_includes_correct_file_path_in_sources(self, mock_store_with_chunks, mock_groq):
        service = QAService(vector_store=mock_store_with_chunks)
        response = service.query(QUESTION_JWT, top_k=TOP_K_3)
        assert any(FILE_AUTH in s.file_path for s in response.sources), \
            "sources should include the expected file path"
