# 🚀 HirePilotAI

> **Fully Autonomous Job Search Agent** — from discovery to application, completely automated.

HirePilotAI is a multi-agent AI system that intelligently matches candidates to high-probability roles, dynamically tailors application materials, and automates submissions — so you can focus 100% on interview prep.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          HirePilotAI                                │
├──────────────────┬──────────────────────┬───────────────────────────┤
│   Frontend       │   Backend (Agents)   │   Infrastructure          │
│   Next.js 14     │   Spring Boot 3.3    │   PostgreSQL 16           │
│   Shadcn UI      │   Java 21            │   Kafka (KRaft)           │
│   Tailwind CSS   │   Spring Kafka       │   Qdrant (Vector DB)      │
│                  │   Liquibase          │   Ollama (Local LLMs)     │
│                  │   Apache Tika        │   Jaeger (Tracing)        │
└──────────────────┴──────────────────────┴───────────────────────────┘
```

## Agent Pipeline

```
Resume Upload → [Qwen3] Parse → Structured JSON
                                    ↓
Job Boards → [Scraper] Normalize → [Qdrant] Vector Match → Score ≥ 80%?
                                                                  ↓ Yes
                                              [Llama 3.1] Tailor Resume + Cover Letter
                                                                  ↓
                                              [DeepSeek-R1] Answer Screening Questions
                                                                  ↓
                                              [Playwright] Fill Form → ⏸ Human Confirm → Submit
                                                                  ↓
                                              Kanban Updated + Interview Prep Sheet Generated
```

## Local LLM Stack (Ollama)

| Model | Role |
|---|---|
| `qwen3:8b` | JD analysis + structured data extraction |
| `llama3.1:8b` | Resume rewriting + cover letter generation |
| `deepseek-r1:8b` | Screening question reasoning + form logic |

## Quick Start

### Prerequisites
- Docker + Docker Compose
- Java 21 (JDK)
- Node.js 20+
- Ollama (`brew install ollama`)

### 1. Pull Ollama Models
```bash
ollama pull qwen3:8b         # Resume parsing + cover letters
ollama pull deepseek-r1:8b   # Interview prep (chain-of-thought reasoning)
```

### 2. Start Infrastructure
```bash
docker-compose up -d
```

### 3. Start Backend
```bash
cd backend
./mvnw spring-boot:run
```

### 4. Start Frontend
```bash
cd frontend
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) to begin.

---

## Feature Status

| Feature | Status |
|---|---|
| Resume Intelligence Engine | 🔨 In Progress |
| Multi-Source Job Aggregator | 🔨 In Progress |
| AI Job Matching Engine | 🔨 In Progress |
| Resume & Cover Letter Optimizer | 📋 Planned |
| Auto-Apply Agent (Human-in-loop) | 📋 Planned |
| Application Tracking + Kanban | 🔨 In Progress |
| Interview Prep Sheet Generator | 📋 Planned |
| OpenTelemetry Observability | 📋 Planned |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 14, React, TypeScript, Tailwind CSS, Shadcn UI |
| Backend | Java 21, Spring Boot 3.3, Spring MVC, Spring Kafka |
| Database | PostgreSQL 16, Spring Data JPA, Liquibase |
| Event Streaming | Apache Kafka (KRaft mode) |
| Vector Search | Qdrant |
| Browser Automation | Playwright (Java) |
| Observability | OpenTelemetry, Jaeger |
| AI / LLMs | Ollama (local) — Qwen3, Llama 3.1, DeepSeek-R1 |

---

## License

MIT