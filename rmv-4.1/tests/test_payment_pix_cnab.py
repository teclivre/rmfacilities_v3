import unittest
import urllib.error
from datetime import date
from types import SimpleNamespace
from unittest.mock import patch

import app as app_module
from app import (
    app as flask_app,
    _cnab240_conta_remessa,
    _cnab240_pix_info,
)


class PixCnabInfoTests(unittest.TestCase):
    def test_uses_registered_phone_pix_key_without_replacing_it_with_cpf(self):
        key = "11987654321"
        self.assertEqual(_cnab240_pix_info(key), ("01", key))

    def test_preserves_pix_copy_and_paste_payload(self):
        payload = "000201abcXYZ"
        self.assertEqual(_cnab240_pix_info(payload), ("05", payload))

    def test_rejects_payload_longer_than_cnab_field(self):
        with self.assertRaisesRegex(ValueError, "99 caracteres"):
            _cnab240_pix_info("1" * 100)

    def test_requires_pix_data(self):
        with self.assertRaisesRegex(ValueError, "informe a chave PIX"):
            _cnab240_pix_info("")

    def test_does_not_use_cpf_when_pix_key_is_missing(self):
        company = SimpleNamespace(
            id=1, cnpj="12345678000199", agencia="12345-6", conta="123456789012-3",
        )
        employee = SimpleNamespace(
            empresa_id=1, nome="Funcionario Teste", cpf="12345678901",
            banco_pix="", banco_codigo="", banco_agencia="", banco_conta="",
            banco_tipo_conta="",
        )
        payment = SimpleNamespace(id=7, funcionario_id=3, total_pagar=100.00)
        items = SimpleNamespace(order_by=lambda _: SimpleNamespace(all=lambda: [payment]))
        payroll = SimpleNamespace(id=9, itens=items)

        with patch("app.db.session.get", return_value=employee):
            with self.assertRaisesRegex(ValueError, "cadastre agência e conta ou chave PIX"):
                _cnab240_conta_remessa(payroll, company, date(2026, 10, 10))

    def test_payroll_remittance_prefers_registered_pix_key_over_complete_bank_account(self):
        company = SimpleNamespace(
            id=1, cnpj="12345678000199", agencia="12345-6", conta="123456789012-3",
            nome="Empresa Teste", logradouro="Rua A", numero="10", cidade="Sao Paulo",
            cep="01001000", estado="SP",
        )
        employee = SimpleNamespace(
            empresa_id=1, nome="Funcionario Teste", cpf="12345678901",
            banco_pix="11987654321", banco_codigo="001", banco_agencia="1234-5",
            banco_conta="123456-7", banco_tipo_conta="corrente",
        )
        payment = SimpleNamespace(id=7, funcionario_id=3, total_pagar=100.00)
        items = SimpleNamespace(order_by=lambda _: SimpleNamespace(all=lambda: [payment]))
        payroll = SimpleNamespace(id=9, itens=items)

        with patch("app.db.session.get", return_value=employee):
            content = _cnab240_conta_remessa(payroll, company, date(2026, 10, 10))

        records = content.splitlines()
        self.assertEqual(records[1][11:13], "45")
        pix_record = next(record for record in records if record[7:8] == "3" and record[13:14] == "B")
        self.assertEqual(pix_record[14:17].strip(), "01")
        self.assertEqual(pix_record[127:226].rstrip(), "11987654321")

class CnpjLookupFallbackTests(unittest.TestCase):
    def test_returns_local_supplier_when_cnpj_already_exists(self):
        local_supplier = SimpleNamespace(
            nome="Fornecedor Local Ltda",
            razao="Fornecedor Local Ltda",
            cnpj="12345678000199",
            email="contato@fornecedor.com",
            telefone="11999999999",
            banco_pix="chave-local-pix",
            ativo=True,
        )

        class DummyFornecedor:
            query = SimpleNamespace(filter_by=lambda **_: SimpleNamespace(first=lambda: local_supplier))

        class DummyEmpresa:
            query = SimpleNamespace(filter_by=lambda **_: SimpleNamespace(first=lambda: None))

        with flask_app.app_context():
            with patch.object(app_module, "Fornecedor", DummyFornecedor), \
                 patch.object(app_module, "Empresa", DummyEmpresa), \
                 patch("app.urllib.request.urlopen", side_effect=urllib.error.URLError("provider down")):
                with flask_app.test_client() as client:
                    with client.session_transaction() as sess:
                        sess["uid"] = 1
                    resp = client.get("/api/cnpj/12345678000199")

        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.get_json()["nome"], "Fornecedor Local Ltda")
        self.assertEqual(resp.get_json()["razao"], "Fornecedor Local Ltda")
        self.assertEqual(resp.get_json()["email"], "contato@fornecedor.com")
        self.assertEqual(resp.get_json()["telefone"], "11999999999")


if __name__ == "__main__":
    unittest.main()