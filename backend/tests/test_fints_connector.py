from datetime import date
from decimal import Decimal
from unittest.mock import patch, MagicMock
from tms.connectors.fints_connector import FinTSConnector


def make_mock_transaction(amount_value, date_val, applicant_name, purpose):
    txn = MagicMock()
    txn_amount = MagicMock()
    txn_amount.amount = Decimal(str(amount_value))
    txn_amount.currency = "EUR"
    txn.data = {
        "amount": txn_amount,
        "date": date_val,
        "applicant_name": applicant_name,
        "purpose": purpose,
        "id": {"reference": f"ref_{abs(int(amount_value * 100))}"},
    }
    return txn


@patch("tms.connectors.fints_connector.FinTS3PinTanClient")
def test_fetch_transactions(mock_client_cls):
    mock_client = MagicMock()
    mock_client_cls.return_value = mock_client
    mock_client.__enter__ = MagicMock(return_value=mock_client)
    mock_client.__exit__ = MagicMock(return_value=False)

    mock_sepa = MagicMock()
    mock_sepa.iban = "DE123456789"
    mock_client.get_sepa_accounts.return_value = [mock_sepa]

    mock_txns = [
        make_mock_transaction(-67.00, date(2026, 4, 5), "Amazon.de", "Order 123"),
        make_mock_transaction(2500.00, date(2026, 4, 1), "Arbeitgeber", "Gehalt April"),
    ]
    mock_client.get_transactions.return_value = mock_txns

    connector = FinTSConnector(
        blz="12345678", login="user", pin="1234",
        endpoint="https://fints.sparkasse.de",
    )
    txns = connector.fetch_transactions("DE123456789", since=date(2026, 4, 1))

    assert len(txns) == 2
    assert txns[0].amount == -67.00
    assert txns[0].merchant_name == "Amazon.de"
    assert txns[1].amount == 2500.00


@patch("tms.connectors.fints_connector.FinTS3PinTanClient")
def test_fetch_accounts(mock_client_cls):
    mock_client = MagicMock()
    mock_client_cls.return_value = mock_client
    mock_client.__enter__ = MagicMock(return_value=mock_client)
    mock_client.__exit__ = MagicMock(return_value=False)

    mock_sepa = MagicMock()
    mock_sepa.iban = "DE123456789"
    mock_client.get_sepa_accounts.return_value = [mock_sepa]

    mock_balance = MagicMock()
    mock_balance.amount.amount = Decimal("12340.50")
    mock_balance.amount.currency = "EUR"
    mock_client.get_balance.return_value = mock_balance

    connector = FinTSConnector(
        blz="12345678", login="user", pin="1234",
        endpoint="https://fints.sparkasse.de",
    )
    accounts = connector.fetch_accounts()

    assert len(accounts) == 1
    assert accounts[0].external_id == "DE123456789"
    assert accounts[0].balance == 12340.50
