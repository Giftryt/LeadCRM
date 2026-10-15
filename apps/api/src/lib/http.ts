import type { NextFunction, Request, RequestHandler, Response } from "express";

/** An error that maps directly to an HTTP response. */
export class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
    public code = "error",
    public details?: unknown,
  ) {
    super(message);
  }
}

export const badRequest = (message: string, details?: unknown) =>
  new HttpError(400, message, "bad_request", details);
export const unauthorized = (message = "Sign in to continue.") => new HttpError(401, message, "unauthorized");
export const forbidden = (message = "You don't have permission to do that.") =>
  new HttpError(403, message, "forbidden");
export const notFound = (what = "Resource") => new HttpError(404, `${what} not found.`, "not_found");
export const conflict = (message: string) => new HttpError(409, message, "conflict");

/** Express 4 does not catch rejected promises; this forwards them to the error handler. */
export const route =
  (fn: (req: Request, res: Response, next: NextFunction) => Promise<unknown>): RequestHandler =>
  (req, res, next) => {
    fn(req, res, next).catch(next);
  };
