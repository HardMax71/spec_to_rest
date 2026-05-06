service AccountService {

  type Email = String where len(value) >= 1 and len(value) <= 100
  type AccountId = Int where value > 0

  entity Account {
    id: AccountId
    email: Email
    password_hash: String where len(value) > 0
    display_name: String where len(value) >= 1
  }

  state {
    accounts: AccountId -> lone Account
    next_id: AccountId
  }

  operation CreateAccount {
    input:  email: Email,
            password_hash: String,
            display_name: String
    output: account: Account

    requires:
      true

    ensures:
      account.id = pre(next_id)
      account.email = email
      account.password_hash = password_hash
      account.display_name = display_name
      next_id' = pre(next_id) + 1
      accounts' = pre(accounts) + {account.id -> account}
      #accounts' = #pre(accounts) + 1
  }

  conventions {
    CreateAccount.http_path = "/accounts"
    CreateAccount.http_method = "POST"
    CreateAccount.http_status_success = 201
  }
}
