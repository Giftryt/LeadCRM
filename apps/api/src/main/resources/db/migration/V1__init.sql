-- Lead Tracker schema. Add new changes as V2__<description>.sql, V3__..., never edit an applied migration.

CREATE TABLE users (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email         text        NOT NULL UNIQUE,
  name          text        NOT NULL,
  password_hash text        NOT NULL,
  role          varchar(16) NOT NULL DEFAULT 'REP' CHECK (role IN ('ADMIN', 'REP', 'VIEWER')),
  active        boolean     NOT NULL DEFAULT true,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE leads (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name             text          NOT NULL,
  company          text,
  email            text,
  phone            text,
  source           varchar(32)   NOT NULL DEFAULT 'WEBSITE'
                   CHECK (source IN ('WEBSITE', 'REFERRAL', 'LINKEDIN', 'COLD_OUTREACH', 'EVENT', 'PARTNER', 'INBOUND_CALL', 'OTHER')),
  stage            varchar(16)   NOT NULL DEFAULT 'NEW'
                   CHECK (stage IN ('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
  value_inr        numeric(14, 2) NOT NULL DEFAULT 0 CHECK (value_inr >= 0),
  next_follow_up   date,
  notes            text,
  lost_reason      text,
  owner_id         uuid REFERENCES users (id) ON DELETE SET NULL,
  created_by_id    uuid REFERENCES users (id) ON DELETE SET NULL,
  stage_changed_at timestamptz   NOT NULL DEFAULT now(),
  won_at           timestamptz,
  last_contact_at  timestamptz,
  created_at       timestamptz   NOT NULL DEFAULT now(),
  updated_at       timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX leads_stage_idx          ON leads (stage);
CREATE INDEX leads_owner_idx          ON leads (owner_id);
CREATE INDEX leads_next_follow_up_idx ON leads (next_follow_up);
CREATE INDEX leads_updated_at_idx     ON leads (updated_at);

CREATE TABLE activities (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  lead_id    uuid        NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
  type       varchar(16) NOT NULL CHECK (type IN ('CALL', 'EMAIL', 'MEETING', 'NOTE', 'STAGE_CHANGE', 'CREATED')),
  body       text        NOT NULL,
  user_id    uuid REFERENCES users (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX activities_lead_created_idx ON activities (lead_id, created_at DESC);
