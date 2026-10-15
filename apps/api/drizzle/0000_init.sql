CREATE TYPE "public"."activity_type" AS ENUM('CALL', 'EMAIL', 'MEETING', 'NOTE', 'STAGE_CHANGE', 'CREATED');--> statement-breakpoint
CREATE TYPE "public"."role" AS ENUM('ADMIN', 'REP', 'VIEWER');--> statement-breakpoint
CREATE TYPE "public"."lead_source" AS ENUM('WEBSITE', 'REFERRAL', 'LINKEDIN', 'COLD_OUTREACH', 'EVENT', 'PARTNER', 'INBOUND_CALL', 'OTHER');--> statement-breakpoint
CREATE TYPE "public"."stage" AS ENUM('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST');--> statement-breakpoint
CREATE TABLE "activities" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"lead_id" uuid NOT NULL,
	"type" "activity_type" NOT NULL,
	"body" text NOT NULL,
	"user_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "leads" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"name" text NOT NULL,
	"company" text,
	"email" text,
	"phone" text,
	"source" "lead_source" DEFAULT 'WEBSITE' NOT NULL,
	"stage" "stage" DEFAULT 'NEW' NOT NULL,
	"value_inr" numeric(14, 2) DEFAULT '0' NOT NULL,
	"next_follow_up" date,
	"notes" text,
	"lost_reason" text,
	"owner_id" uuid,
	"created_by_id" uuid,
	"stage_changed_at" timestamp with time zone DEFAULT now() NOT NULL,
	"won_at" timestamp with time zone,
	"last_contact_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"email" text NOT NULL,
	"name" text NOT NULL,
	"password_hash" text NOT NULL,
	"role" "role" DEFAULT 'REP' NOT NULL,
	"active" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "users_email_unique" UNIQUE("email")
);
--> statement-breakpoint
ALTER TABLE "activities" ADD CONSTRAINT "activities_lead_id_leads_id_fk" FOREIGN KEY ("lead_id") REFERENCES "public"."leads"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "activities" ADD CONSTRAINT "activities_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "leads" ADD CONSTRAINT "leads_owner_id_users_id_fk" FOREIGN KEY ("owner_id") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "leads" ADD CONSTRAINT "leads_created_by_id_users_id_fk" FOREIGN KEY ("created_by_id") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "activities_lead_created_idx" ON "activities" USING btree ("lead_id","created_at");--> statement-breakpoint
CREATE INDEX "leads_stage_idx" ON "leads" USING btree ("stage");--> statement-breakpoint
CREATE INDEX "leads_owner_idx" ON "leads" USING btree ("owner_id");--> statement-breakpoint
CREATE INDEX "leads_next_follow_up_idx" ON "leads" USING btree ("next_follow_up");--> statement-breakpoint
CREATE INDEX "leads_updated_at_idx" ON "leads" USING btree ("updated_at");