# Deploying the Pulse API to Render

This service runs the verified backend (Spring Boot 4.0.4 / Java 25 / PostgreSQL) on Render
(live at https://pulse-o3gj.onrender.com) for team testing and as the target URL for the
mobile API layer (P5).

## One-time setup (≈ 10 min)

1. **Create a free Neon Postgres project** → [neon.tech](https://neon.tech)
   - New project → region `EU Central (Frankfurt)` → copy the **connection string**:
     `postgresql://neondb_owner:xxx@ep-xxx.eu-central-1.aws.neon.tech/pulse_db?sslmode=require`
2. **Create a Render account** → [render.com](https://render.com) (email + card; free tier OK)
3. **Deploy the blueprint**:
   - Render dashboard → **New → Blueprint** → connect `psam-717/pulse` → it reads `render.yaml`
   - Service `pulse` is created with placeholders → fill:
     | Env var | Value |
     |---|---|
     | `DB_URL` | your Neon connection string |
     | `DB_USERNAME` | Neon user (e.g. `neondb_owner`) |
     | `DB_PASSWORD` | Neon password |
     | `JWT_SECRET` | auto-generated (leave) |
     | `OTP_DEV_MODE` | `true` (echoes OTPs for testing) |
   - **Deploy** → first build takes ~5 min (Maven + image pull)
4. **Verify**: open `https://pulse-o3gj.onrender.com/api/status` → `{"status":"up",...}`

## Keeping the free tier awake

Free web services spin down after 15 min idle (first request after sleep = slow cold start).
Pick one keep-alive (every ~10 min):

- **Hermes cron (simplest)**: a `no_agent` cron pings `/api/status` every 10 min —
  already running as the **Pulse API Keep-Alive** cron (silent when healthy, Telegram
  alert on failure).
- **UptimeRobot (external)**: free monitor on `https://pulse-o3gj.onrender.com/api/status`,
  5-min interval — zero local resources.
- **DO droplet cron**: if you keep the droplet, add
  `*/10 * * * * curl -s -o /dev/null https://pulse-o3gj.onrender.com/api/status`

## Redeploys

Push to `main` → Render auto-redeploys. No manual steps.

## Gotchas

- **Free tier = 512 MB RAM / 0.1 CPU.** The Dockerfile caps the JVM at 300 MB heap.
  If the seeder OOMs on first boot, the fix is `plan: starter` ($7/mo, still 512 MB but
  guaranteed uptime) or `plan: standard`.
- **File uploads are ephemeral** on Render (container disk resets each deploy) — fine for
  testing license uploads; a real store (S3/Cloudinary) comes before launch.
- **OTP dev-mode is ON** — anyone with the URL sees OTPs. Flip `OTP_DEV_MODE=false` + add a
  real email sender before launch.
- **CORS** allows only localhost origins — native mobile apps don't send CORS headers, so
  the app works; the web dashboard needs its own origin added when it's deployed.
