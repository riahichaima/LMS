"""Génération de quiz / TP — appelée par Spring Boot (AssessmentService)."""

from fastapi import APIRouter, HTTPException

from api.schemas import GenerateAssessmentsRequest
from services.assessment_service import generate_assessments

router = APIRouter()


@router.post("/generate-assessments")
def generate(request: GenerateAssessmentsRequest):
    """Analyser les ressources du cours (PDF, vidéo, liens) et générer quiz + corrections."""
    try:
        return generate_assessments(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
