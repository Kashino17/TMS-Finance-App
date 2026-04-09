from datetime import date
from unittest.mock import patch, MagicMock
from tms.connectors.lean import LeanConnector


MOCK_ACCOUNTS_RESPONSE = {
    "results": [
        {
            "account_id": "acc_123",
            "name": "Current Account",
            "currency": "AED",
            "balance": {"amount": 52100.0, "currency": "AED"},
        }
    ]
}

MOCK_TRANSACTIONS_RESPONSE = {
    "results": [
        {
            "transaction_id": "txn_001",
            "amount": {"amount": -234.50, "currency": "AED"},
            "date": "2026-04-08",
            "description": "POS Purchase - Carrefour",
            "merchant": {"name": "Carrefour"},
        },
        {
            "transaction_id": "txn_002",
            "amount": {"amount": 15000.00, "currency": "AED"},
            "date": "2026-04-01",
            "description": "Salary Credit",
            "merchant": None,
        },
    ]
}

MOCK_TOKEN_RESPONSE = {
    "access_token": "mock_access_token_abc123",
    "expires_in": 3600,
}


@patch("tms.connectors.lean.httpx")
def test_fetch_accounts(mock_httpx):
    # OAuth token POST response
    token_resp = MagicMock()
    token_resp.json.return_value = MOCK_TOKEN_RESPONSE
    token_resp.raise_for_status = MagicMock()

    # Accounts GET response
    accounts_resp = MagicMock()
    accounts_resp.json.return_value = MOCK_ACCOUNTS_RESPONSE
    accounts_resp.raise_for_status = MagicMock()

    mock_httpx.post.return_value = token_resp
    mock_httpx.get.return_value = accounts_resp

    connector = LeanConnector(app_id="test_app", client_secret="test_secret", customer_id="cust_123")
    accounts = connector.fetch_accounts()

    assert len(accounts) == 1
    assert accounts[0].external_id == "acc_123"
    assert accounts[0].balance == 52100.0
    assert accounts[0].currency == "AED"


@patch("tms.connectors.lean.httpx")
def test_fetch_transactions(mock_httpx):
    # OAuth token POST response
    token_resp = MagicMock()
    token_resp.json.return_value = MOCK_TOKEN_RESPONSE
    token_resp.raise_for_status = MagicMock()

    # Transactions GET response
    txns_resp = MagicMock()
    txns_resp.json.return_value = MOCK_TRANSACTIONS_RESPONSE
    txns_resp.raise_for_status = MagicMock()

    mock_httpx.post.return_value = token_resp
    mock_httpx.get.return_value = txns_resp

    connector = LeanConnector(app_id="test_app", client_secret="test_secret", customer_id="cust_123")
    txns = connector.fetch_transactions("acc_123", since=date(2026, 4, 1))

    assert len(txns) == 2
    assert txns[0].external_id == "txn_001"
    assert txns[0].amount == -234.50
    assert txns[0].merchant_name == "Carrefour"
    assert txns[1].amount == 15000.00
