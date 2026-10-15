import type { ErrorRequestHandler, RequestHandler } from "express";
import { ZodError } from "zod";
import { HttpError } from "../lib/http.js";

export const notFoundHandler: RequestHandler = (_req, res) => {
  res.status(404).json({ error: { code: "not_found", message: "No such endpoint." } });
};

/** PostgreSQL error codes we translate into client errors. */
const PG_UNIQUE_VIOLATION = "23505";
const PG_INVALID_TEXT = "22P02"; // e.g. a malformed UUID in a URL

export const errorHandler: ErrorRequestHandler = (err, req, res, _next) => {
  if (err instanceof ZodError) {
    const first = err.issues[0];
    const field = first?.path.join(".");
    return res.status(400).json({
      error: {
        code: "validation_error",
        message: field ? `${field}: ${first.message}` : "The request is not valid.",
        details: err.issues.map((i) => ({ field: i.path.join("."), message: i.message })),
      },
    });
  }
  if (err instanceof HttpError) {
    return res
      .status(err.status)
      .json({ error: { code: err.code, message: err.message, details: err.details } });
  }
  const pgCode = err?.code ?? err?.cause?.code;
  if (pgCode === PG_UNIQUE_VIOLATION) {
    return res
      .status(409)
      .json({ error: { code: "conflict", message: "A record with that value already exists." } });
  }
  if (pgCode === PG_INVALID_TEXT) {
    return res.status(404).json({ error: { code: "not_found", message: "Record not found." } });
  }
  if (err?.type === "entity.parse.failed") {
    return res
      .status(400)
      .json({ error: { code: "bad_json", message: "The request body is not valid JSON." } });
  }
  if (err?.code === "LIMIT_FILE_SIZE") {
    return res
      .status(413)
      .json({ error: { code: "file_too_large", message: "The file is larger than 5 MB." } });
  }
  req.log?.error({ err }, "Unhandled error");
  res.status(500).json({ error: { code: "internal", message: "Something went wrong on the server." } });
};
