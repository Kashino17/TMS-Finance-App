from datetime import date
from unittest.mock import patch, MagicMock
from tms.connectors.revolut import RevolutConnector

MOCK_ACCOUNTS = [
    {"id": "rev_acc_1", "name": "EUR Wallet", "currency": "EUR", "balance": 750000},
    {"id": "rev_acc_2", "name": "AED Wallet", "currency": "AED", "balance": 289200},
]

MOCK_TRANSACTIONS = [
    {
        "id": "rev_txn_1",
        "amount": -4500,
        "currency": "AED",
        "created_at": "2026-04-07T14:30:00Z",
        "description": "Uber",
        "merchant": {"name": "Uber"},
    },
    {
        "id": "rev_txn_2",
        "amount": -6700,
        "currency": "EUR",
        "created_at": "2026-04-05T10:00:00Z",
        "description": "Amazon.de",
        "merchant": {"name": "Amazon.de"},
    },
]


@patch("tms.connectors.revolut.httpx")
def test_fetch_accounts(mock_httpx):
    resp = MagicMock()
    resp.json.return_value = MOCK_ACCOUNTS
    resp.raise_for_status = MagicMock()
    mock_httpx.get.return_value = resp

    connector = RevolutConnector(access_token="test")
    accounts = connector.fetch_accounts()

    assert len(accounts) == 2
    assert accounts[0].currency == "EUR"
    assert accounts[0].balance == 7500.00  # Minor units converted


@patch("tms.connectors.revolut.httpx")
def test_fetch_transactions(mock_httpx):
    resp = MagicMock()
    resp.json.return_value = MOCK_TRANSACTIONS
    resp.raise_for_status = MagicMock()
    mock_httpx.get.return_value = resp

    connector = RevolutConnector(access_token="test")
    txns = connector.fetch_transactions("rev_acc_1", since=date(2026, 4, 1))

    assert len(txns) == 2
    assert txns[0].amount == -45.00  # Minor units converted
    assert txns[0].merchant_name == "Uber"
