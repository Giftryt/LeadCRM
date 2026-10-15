import cookieParser from "cookie-parser";
import cors from "cors";
import express from "express";
import helmet from "helmet";
import { pinoHttp } from "pino-http";
import { config, isProd } from "./config.js";
import { errorHandler, notFoundHandler } from "./middleware/error.js";
import { pool } from "./db/index.js";
import { authRouter } from "./routes/auth.js";
import { dashboardRouter } from "./routes/dashboard.js";
import { leadsRouter } from "./routes/leads.js";
import { usersRouter } from "./routes/users.js";

export function createApp() {
  const app = express();
  app.disable("x-powered-by");
  app.set("trust proxy", 1); // behind nginx / a load balancer in production

  app.use(helmet());
  if (config.CORS_ORIGIN) {
    app.use(cors({ origin: config.CORS_ORIGIN.split(",").map((s) => s.trim()), credentials: true }));
  }
  if (config.NODE_ENV !== "test") {
    app.use(
      pinoHttp({
        level: isProd ? "info" : "debug",
        redact: ["req.headers.cookie", "req.headers.authorization", 'res.headers["set-cookie"]'],
        autoLogging: { ignore: (req) => req.url === "/api/health" },
      }),
    );
  }
  app.use(express.json({ limit: "1mb" }));
  app.use(cookieParser());

  app.get("/api/health", async (_req, res) => {
    try {
      await pool.query("SELECT 1");
      res.json({ status: "ok" });
    } catch {
      res.status(503).json({ status: "degraded", database: "unreachable" });
    }
  });

  app.use("/api/auth", authRouter);
  app.use("/api/users", usersRouter);
  app.use("/api/leads", leadsRouter);
  app.use("/api/dashboard", dashboardRouter);

  app.use("/api", notFoundHandler);
  app.use(errorHandler);
  return app;
}
