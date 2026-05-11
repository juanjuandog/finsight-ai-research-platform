from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import Any

app = FastAPI(title="FinSight AI Service", version="0.1.0")


class EvidenceChunk(BaseModel):
    documentId: str
    title: str
    documentType: str
    publishedAt: str | None = None
    section: str
    text: str
    score: float


class RerankRequest(BaseModel):
    question: str
    candidates: list[EvidenceChunk]


class RerankResponse(BaseModel):
    evidence: list[EvidenceChunk]


class GenerateAnswerRequest(BaseModel):
    question: str
    structuredQuery: dict[str, Any] = Field(default_factory=dict)
    evidence: list[EvidenceChunk]


class GenerateAnswerResponse(BaseModel):
    answer: str


class ParseDocumentRequest(BaseModel):
    documentId: str
    content: str


class ParsedSection(BaseModel):
    section: str
    text: str
    tags: list[str] = Field(default_factory=list)


class ParseDocumentResponse(BaseModel):
    documentId: str
    sections: list[ParsedSection]


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/rerank", response_model=RerankResponse)
def rerank(request: RerankRequest) -> RerankResponse:
    question_terms = set(request.question.lower().split())

    def score(chunk: EvidenceChunk) -> float:
        text_terms = set(chunk.text.lower().split())
        overlap = len(question_terms & text_terms)
        return chunk.score + overlap * 0.03

    ranked = sorted(
        (chunk.model_copy(update={"score": score(chunk)}) for chunk in request.candidates),
        key=lambda chunk: chunk.score,
        reverse=True,
    )
    return RerankResponse(evidence=ranked)


@app.post("/generate-answer", response_model=GenerateAnswerResponse)
def generate_answer(request: GenerateAnswerRequest) -> GenerateAnswerResponse:
    evidence_text = " ".join(chunk.text for chunk in request.evidence[:3])
    citations = "、".join(
        f"{chunk.title}/{chunk.section}" for chunk in request.evidence[:3]
    )
    intent = request.structuredQuery.get("intent", "FINANCIAL_QA")
    answer = (
        f"分析意图：{intent}。基于证据可见，{evidence_text} "
        f"引用来源：{citations}。本结果只做信息整理和风险提示，不构成投资建议。"
    )
    return GenerateAnswerResponse(answer=answer)


@app.post("/parse-document", response_model=ParseDocumentResponse)
def parse_document(request: ParseDocumentRequest) -> ParseDocumentResponse:
    parts = [part.strip() for part in request.content.split("。") if part.strip()]
    sections = [
        ParsedSection(section=f"section-{idx + 1}", text=part, tags=infer_tags(part))
        for idx, part in enumerate(parts[:12])
    ]
    return ParseDocumentResponse(documentId=request.documentId, sections=sections)


def infer_tags(text: str) -> list[str]:
    tags: list[str] = []
    if any(keyword in text for keyword in ("营收", "净利润", "毛利率", "现金流")):
        tags.append("financial_metric")
    if any(keyword in text for keyword in ("风险", "不确定性", "压力")):
        tags.append("risk")
    if any(keyword in text for keyword in ("行业", "需求", "库存")):
        tags.append("industry")
    return tags

