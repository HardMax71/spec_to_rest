service AuthService {

  // --- Types ---

  type Email = String where value matches /^[^@]+@[^@]+\.[^@]+$/
  type PasswordHash = String where len(value) = 64
  type Token = String where len(value) = 128
  type UserId = Int where value > 0

  // --- Enums ---

  enum TokenType {
    ACCESS,
    REFRESH,
    RESET
  }

  // --- Entities ---

  entity User {
    id: UserId
    email: Email
    password_hash: PasswordHash
    display_name: String where len(value) >= 1 and len(value) <= 100
    created_at: DateTime
    last_login: Option[DateTime]
    is_active: Bool

    invariant: len(email) > 0
  }

  entity Session {
    id: Int where value > 0
    user_id: UserId
    access_token: Token
    refresh_token: Token
    access_expires_at: DateTime
    refresh_expires_at: DateTime
    created_at: DateTime
    is_revoked: Bool

    invariant: access_expires_at > created_at
    invariant: refresh_expires_at > access_expires_at
    invariant: access_token != refresh_token
  }

  entity LoginAttempt {
    email: Email
    timestamp: DateTime
    success: Bool
  }

  // --- State ---

  state {
    users: UserId -> lone User
    sessions: Int -> lone Session
    login_attempts: Seq[LoginAttempt]
    user_by_email: Email -> lone User
    next_user_id: UserId
    next_session_id: Int
  }

  // --- Operations ---

  operation Register {
    input:  email: Email, password: String,
            display_name: String
    output: user: User

    requires:
      email not in user_by_email
      len(password) >= 8
      len(display_name) >= 1

    ensures:
      user.id = pre(next_user_id)
      user.email = email
      user.password_hash = hash(password)
      user.display_name = display_name
      user.is_active = true
      user.last_login = none
      next_user_id' = pre(next_user_id) + 1
      users' = pre(users) + {user.id -> user}
      user_by_email' = pre(user_by_email) + {email -> user}
      #users' = #pre(users) + 1
  }

  operation Login {
    input:  email: Email, password: String
    output: session: Session

    requires:
      email in user_by_email
      user_by_email[email].is_active = true
      user_by_email[email].password_hash = hash(password)
      recentFailedAttempts(email) < 5

    ensures:
      let user = user_by_email[email] in
        session.user_id = user.id
        and session.is_revoked = false
        and session.access_expires_at > now()
        and session.refresh_expires_at > session.access_expires_at
        and sessions' = pre(sessions) + {session.id -> session}
        and users'[user.id].last_login = some(now())
        and login_attempts' = pre(login_attempts)
             + [LoginAttempt { email = email,
                               timestamp = now(),
                               success = true }]
  }

  operation LoginFailed {
    input: email: Email, password: String

    requires:
      email in user_by_email
      user_by_email[email].password_hash != hash(password)

    ensures:
      sessions' = sessions
      users' = users
      login_attempts' = pre(login_attempts)
        + [LoginAttempt { email = email,
                          timestamp = now(),
                          success = false }]
  }

  operation RefreshToken {
    input:  refresh_token: Token
    output: new_session: Session

    requires:
      some s in sessions |
        sessions[s].refresh_token = refresh_token
        and sessions[s].is_revoked = false
        and sessions[s].refresh_expires_at > now()

    ensures:
      let old_session = (the s in sessions |
        sessions[s].refresh_token = refresh_token) in
        sessions'[old_session].is_revoked = true
        and new_session.user_id = pre(sessions)[old_session].user_id
        and new_session.is_revoked = false
        and new_session.access_expires_at > now()
        and sessions' = pre(sessions)
             + {old_session -> pre(sessions)[old_session]
                  with { is_revoked = true }}
             + {new_session.id -> new_session}
  }

  operation RequestPasswordReset {
    input:  email: Email
    output: reset_token: Token

    requires:
      email in user_by_email
      user_by_email[email].is_active = true

    ensures:
      some s in sessions' |
        s not in pre(sessions)
        and sessions'[s].user_id = user_by_email[email].id
        and sessions'[s].access_expires_at > now()
      users' = users
  }

  operation ResetPassword {
    input:  reset_token: Token, new_password: String

    requires:
      some s in sessions |
        sessions[s].access_token = reset_token
        and sessions[s].is_revoked = false
        and sessions[s].access_expires_at > now()
      len(new_password) >= 8

    ensures:
      let s = (the s in sessions |
        sessions[s].access_token = reset_token) in
        let user_id = pre(sessions)[s].user_id in
          users'[user_id].password_hash = hash(new_password)
          and sessions'[s].is_revoked = true
  }

  operation Logout {
    input: access_token: Token

    requires:
      some s in sessions |
        sessions[s].access_token = access_token
        and sessions[s].is_revoked = false

    ensures:
      let s = (the s in sessions |
        sessions[s].access_token = access_token) in
        sessions'[s].is_revoked = true
        and users' = users
  }

  // --- Helper Functions ---

  fact recentFailedAttemptsDef:
    all email in dom(user_by_email) |
      recentFailedAttempts(email) =
        #{ a in login_attempts |
           a.email = email
           and a.success = false
           and a.timestamp > now() - minutes(15) }

  // --- Global Invariants ---

  invariant uniqueEmails:
    all u1 in users, u2 in users |
      u1 != u2 implies users[u1].email != users[u2].email

  invariant emailIndexConsistent:
    all email in user_by_email |
      user_by_email[email] in ran(users)
      and user_by_email[email].email = email

  invariant sessionBelongsToUser:
    all s in sessions |
      sessions[s].user_id in users

  invariant revokedSessionsStayRevoked:
    all s in sessions |
      pre(sessions)[s].is_revoked = true implies sessions'[s].is_revoked = true

  invariant accessTokensUnique:
    all s1 in sessions, s2 in sessions |
      s1 != s2 implies
        sessions[s1].access_token != sessions[s2].access_token

  invariant rateLimitEnforced:
    all email in user_by_email |
      recentFailedAttempts(email) < 5 or
        (no op in sessions | op = email)

  // --- Conventions ---

  conventions {
    Register.http_path = "/auth/register"
    Register.http_status_success = 201

    Login.http_path = "/auth/login"
    Login.http_method = "POST"
    Login.http_status_success = 200

    RefreshToken.http_path = "/auth/refresh"
    RefreshToken.http_method = "POST"

    RequestPasswordReset.http_path = "/auth/password-reset"
    RequestPasswordReset.http_method = "POST"

    ResetPassword.http_path = "/auth/password-reset/confirm"
    ResetPassword.http_method = "POST"

    Logout.http_path = "/auth/logout"
    Logout.http_method = "POST"
    Logout.http_status_success = 204
  }
}
