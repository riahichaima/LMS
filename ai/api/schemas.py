"""Schémas des requêtes envoyées par le backend Spring Boot."""

from typing import Optional, Union

from pydantic import AliasChoices, BaseModel, Field

DEFAULT_LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"]


class ResourceDetail(BaseModel):
    type: str = ""  # PDF, VIDEO, YOUTUBE_VIDEO, URL, WEB_PAGE, TEXT
    title: str = ""
    file_path: Optional[str] = None
    external_url: Optional[str] = None
    mime_type: Optional[str] = None


class GenerateAssessmentsRequest(BaseModel):
    generate_quiz: bool = True
    generate_assignment: bool = False
    quiz_question_count: int = Field(5, ge=1, le=30)
    assignment_exercise_count: int = Field(3, ge=1, le=10)
    difficulty_levels: Optional[list[str]] = None
    lesson_descriptions: list[Optional[str]] = []
    resource_details: list[ResourceDetail] = []


class TPCorrectionRequest(BaseModel):
    question: str
    student_answer: str
    expected_answer: str = ""
    response_type: str = "text"
    level: str = "intermediate"


class QuizCorrectionRequest(BaseModel):
    # JSON {"1": "réponse", ...} — en chaîne (tel que reçu du frontend) ou en objet
    student_answers: Union[str, dict] = "{}"
    quiz_title: str = ""
    difficulty_level: str = "BEGINNER"
    questions: list[dict] = []


class ProgressAnalysisRequest(BaseModel):
    student_id: Optional[int] = None
    course_id: Optional[int] = None
    average_score: Optional[float] = 0.0


# ── Assistant IA (page "AI Assistant" du frontend, via LmsAiService) ──

class QuestionRequest(BaseModel):
    # Le backend envoie du camelCase (contentId, rawContent)
    content_id: Optional[int] = Field(None, validation_alias=AliasChoices("contentId", "content_id"))
    question: str
    level: str = "intermediate"
    raw_content: Optional[str] = Field(None, validation_alias=AliasChoices("rawContent", "raw_content"))


class URLIngestRequest(BaseModel):
    url: str
    title: Optional[str] = None


# ── Chatbot du cours (page étudiant, via Spring Boot) ──

class ChatMessage(BaseModel):
    role: str  # "user" ou "assistant"
    content: str


class CourseChatRequest(BaseModel):
    course_title: str = "Cours"
    lesson_title: Optional[str] = None  # leçon ouverte par l'étudiant (sinon : tout le cours)
    lesson_descriptions: list[Optional[str]] = []
    resource_details: list[ResourceDetail] = []
    messages: list[ChatMessage] = []
    action: str = "chat"  # "chat" ou "summary"
