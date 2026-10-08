import unittest
import urllib.error
from types import SimpleNamespace
from unittest.mock import patch

import app as app_module
from app import app as flask_app, _cnab240_fornecedor_pix_remessa, _cnab240_pix_info


class PixCnabInfoTests(unittest.TestCase):
    def test_identifies_cpf_pix_key(self):
        self.assertEqual(_cnab240_pix_info("12345678901", ""), ("03", "12345678901"))

    def test_preserves_pix_copy_and_paste_payload(self):
        payload = "000201abcXYZ"
        self.assertEqual(_cnab240_pix_info("", payload), ("05", payload))

    def test_rejects_payload_longer_than_cnab_field(self):
        with self.assertRaisesRegex(ValueError, "99 caracteres"):
            _cnab240_pix_info("", "1" * 100)

    def test_requires_pix_data(self):
        with self.assertRaisesRegex(ValueError, "informe a chave PIX"):
            _cnab240_pix_info("", "")

    def test_generates_240_character_records_with_exact_payload(self):
        company = SimpleNamespace(
            cnpj="12345678000199", agencia="12345-6", conta="123456789012-3",
            nome="Empresa Teste", logradouro="Rua A", numero="10", cidade="Sao Paulo",
            cep="01001000", estado="SP",
        )
        supplier = SimpleNamespace(nome="Fornecedor", cnpj="12345678000199", banco_pix="")
        payment_one = SimpleNamespace(
            id=7, fornecedor_id=3, vencimento="2026-10-10", valor=25.50,
            pix_copia_cola="000201abcXYZ",
        )
        payment_two = SimpleNamespace(
            id=8, fornecedor_id=3, vencimento="2026-10-11", valor=12.50,
            pix_copia_cola="000201second",
        )
        with patch("app.db.session.get", return_value=supplier):
            content = _cnab240_fornecedor_pix_remessa(company, [payment_one, payment_two])

        records = content.splitlines()
        self.assertTrue(all(len(record) == 240 for record in records))
        self.assertEqual(records[3][127:127 + len(payment_one.pix_copia_cola)], payment_one.pix_copia_cola)
        self.assertEqual(records[5][127:127 + len(payment_two.pix_copia_cola)], payment_two.pix_copia_cola)
        self.assertEqual(records[6][17:23], "000006")
        self.assertEqual(records[6][23:41], "000000000000003800")


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