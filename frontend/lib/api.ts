const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  });
  if (!res.ok) {
    const error = await res.text();
    throw new Error(`API ${res.status}: ${error}`);
  }
  return res.json() as Promise<T>;
}

// ─── Types ─────────────────────────────────────────────────────────────────

export interface User {
  id: string;
  name: string;
  email: string;
  targetRoles: string[];
  targetLocations: string[];
  minimumSalary: number;
  createdAt: string;
}

export interface Resume {
  id: string;
  userId: string;
  originalFilename: string;
  parsedData: Record<string, unknown>;
  skills: string[];
  experienceYears: number;
  parsingStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
}

export interface Job {
  id: string;
  title: string;
  company: string;
  source: string;
  url: string;
  location: string;
  isRemote: boolean;
  salaryMin?: number;
  salaryMax?: number;
  requiredSkills: string[];
  experienceLevel: string;
  scrapedAt: string;
}

export interface Application {
  id: string;
  jobId: string;
  jobTitle: string;
  company: string;
  jobUrl?: string;
  status: ApplicationStatus;
  matchScore: number;
  tailoredResumeText?: string;
  coverLetterText?: string;
  confirmationToken?: string;
  notes?: string;
  appliedAt?: string;
  createdAt: string;
}

export type ApplicationStatus =
  // System-managed
  | 'QUEUED' | 'TAILORING' | 'PENDING_CONFIRMATION' | 'APPLYING' | 'FAILED'
  // Human-managed
  | 'APPLIED' | 'SHORTLISTED' | 'ASSESSMENT'
  | 'ROUND_1' | 'ROUND_1_CLEARED'
  | 'ROUND_2' | 'ROUND_2_CLEARED'
  | 'ROUND_3' | 'ROUND_3_CLEARED'
  | 'HR_ROUND' | 'WAITING_FOR_HR'
  | 'OFFER' | 'OFFER_ACCEPTED' | 'OFFER_DECLINED'
  | 'REJECTED' | 'GHOSTED' | 'WITHDRAWN';

export interface KanbanBoard {
  PENDING_CONFIRMATION: Application[];
  APPLIED: Application[];
  ASSESSMENT: Application[];
  INTERVIEW: Application[];
  OFFER: Application[];
  REJECTED: Application[];
  FAILED: Application[];
}

export interface PrepSheet {
  id: string;
  applicationId: string;
  technicalQuestions: string[];
  systemDesignScenarios: string[];
  behavioralQuestions: string[];
  companyResearch?: string;
  generationStatus: 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED';
}

export interface MatchScore {
  totalScore: number;
  skillScore: number;
  experienceScore: number;
  keywordScore: number;
  locationScore: number;
}

// ─── User API ──────────────────────────────────────────────────────────────

export const api = {
  users: {
    create: (data: { name: string; email: string; targetRoles?: string[]; targetLocations?: string[]; minimumSalary?: number }) =>
      request<User>('/api/users', { method: 'POST', body: JSON.stringify(data) }),

    get: (id: string) => request<User>(`/api/users/${id}`),

    updatePreferences: (id: string, prefs: { targetRoles?: string[]; targetLocations?: string[]; minimumSalary?: number }) =>
      request<User>(`/api/users/${id}/preferences`, { method: 'PUT', body: JSON.stringify(prefs) }),
  },

  resumes: {
    upload: async (file: File, userId: string): Promise<Resume> => {
      const form = new FormData();
      form.append('file', file);
      form.append('userId', userId);
      const res = await fetch(`${API_BASE}/api/resumes/upload`, { method: 'POST', body: form });
      if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
      return res.json();
    },

    listByUser: (userId: string) => request<Resume[]>(`/api/resumes/user/${userId}`),
    get: (id: string) => request<Resume>(`/api/resumes/${id}`),
  },

  jobs: {
    list: (page = 0, size = 20) => request<{ content: Job[]; totalElements: number; totalPages: number }>(`/api/jobs?page=${page}&size=${size}`),
    get: (id: string) => request<Job>(`/api/jobs/${id}`),
    scrapeYC: () => request<string>('/api/jobs/scrape/yc', { method: 'POST' }),
    scrapeGreenhouse: (slug: string) => request<string>(`/api/jobs/scrape/greenhouse/${slug}`, { method: 'POST' }),
    computeMatch: (jobId: string, userId: string) =>
      request<MatchScore>(`/api/jobs/${jobId}/match/${userId}`, { method: 'POST' }),
  },

  applications: {
    listByUser: (userId: string) => request<Application[]>(`/api/applications/user/${userId}`),
    kanban: (userId: string) => request<KanbanBoard>(`/api/applications/user/${userId}/kanban`),
    get: (id: string) => request<Application>(`/api/applications/${id}`),
    confirm: (id: string, confirmationToken: string) =>
      request<Application>(`/api/applications/${id}/confirm`, {
        method: 'POST',
        body: JSON.stringify({ confirmationToken }),
      }),
    updateStatus: (id: string, status: ApplicationStatus) =>
      request<Application>(`/api/applications/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      }),
    updateNotes: (id: string, notes: string) =>
      request<Application>(`/api/applications/${id}/notes`, {
        method: 'PATCH',
        body: JSON.stringify({ notes }),
      }),
    evaluate: (userId: string, jobId: string) =>
      request<string>(`/api/applications/evaluate?userId=${userId}&jobId=${jobId}`, { method: 'POST' }),
  },

  prep: {
    get: (applicationId: string) => request<PrepSheet>(`/api/prep/application/${applicationId}`),
    generate: (applicationId: string) =>
      request<PrepSheet>(`/api/prep/application/${applicationId}/generate`, { method: 'POST' }),
  },
};
