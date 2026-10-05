import "@testing-library/jest-dom/vitest";
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "./handlers";

beforeAll(() => {
  // Bypass requests MSW does not handle (e.g. react-router's internal
  // lazy-loaded route discovery). Tests assert against handled endpoints only.
  server.listen({ onUnhandledRequest: "bypass" });
});

afterEach(() => {
  server.resetHandlers();
});

afterAll(() => {
  server.close();
});