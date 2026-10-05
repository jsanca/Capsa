/// <reference types="vite/client" />

const rawBaseUrl = import.meta.env.VITE_CAPSA_API_URL;

if (typeof rawBaseUrl !== "string" || rawBaseUrl.length === 0) {
  throw new Error(
    "VITE_CAPSA_API_URL is not configured. Copy .env.example to .env and set it.",
  );
}

export const capsaApiUrl: string = rawBaseUrl.replace(/\/+$/, "");

export const apiNamespace = "/capsa/api";