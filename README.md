## Authentication & email verification

- Sign up: `POST /auth/signup { email, displayName, password }` (password ≥ 8 chars). The account
  starts unverified and cannot log in until verified.
- A verification email is sent via SMTP. In local/Docker runs it is captured by **Mailpit** — open
  http://localhost:8025 to read it and click the verification link.
- For real delivery (e.g. `relay1.dataart.com`), set `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
  `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Never commit SMTP credentials.
- Verification links expire after 24 hours and are single-use; request a new one with
  `POST /auth/resend { email }`.
- Log in: `POST /auth/login { email, password }` establishes a session cookie. Log out:
  `POST /auth/logout`. Current user: `GET /auth/me`.
- All other endpoints require an authenticated, verified session.
