import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

import app as app_module
from app import app as flask_app


class AppPasswordResetTests(unittest.TestCase):
    def setUp(self):
        self.funcionario = SimpleNamespace(
            id=42,
            cpf="12345678901",
            nome="Funcionario Teste",
            telefone="(11) 99999-8888",
            app_ativo=True,
            status="Ativo",
            data_demissao="",
            app_senha="senha-anterior",
            app_trocar_senha=False,
        )

        class DummyFuncionario:
            query = SimpleNamespace(
                filter_by=Mock(
                    side_effect=lambda **filters: SimpleNamespace(
                        first=lambda: self.funcionario
                        if filters.get("cpf") == self.funcionario.cpf
                        else None
                    )
                )
            )

        revoke_update = Mock()
        session_query = SimpleNamespace(
            filter_by=Mock(return_value=SimpleNamespace(update=revoke_update))
        )
        self.revoke_update = revoke_update
        self.patches = [
            patch.object(app_module, "Funcionario", DummyFuncionario),
            patch.object(
                app_module,
                "FuncionarioAppSessao",
                SimpleNamespace(query=session_query),
            ),
            patch.object(app_module, "auth_blocked", return_value=False),
            patch.object(app_module, "reg_auth_attempt"),
            patch.object(app_module, "pw_hash", side_effect=lambda value: f"hash:{value}"),
            patch.object(app_module, "_db_commit_retry", return_value=True),
            patch.object(app_module, "wa_send_text"),
            patch.object(app_module, "audit_event"),
        ]
        self.mocks = [p.start() for p in self.patches]
        self.addCleanup(self.stop_patches)

    def stop_patches(self):
        for patcher in reversed(self.patches):
            patcher.stop()

    def test_matching_cpf_and_registered_phone_sends_temporary_cpf_password(self):
        with flask_app.test_client() as client:
            response = client.post(
                "/api/app/funcionario/auth/esqueci-senha",
                json={"cpf": "123.456.789-01", "telefone": "11999998888"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.get_json()["ok"])
        self.assertEqual(self.funcionario.app_senha, "hash:12345678901")
        self.assertTrue(self.funcionario.app_trocar_senha)
        self.revoke_update.assert_called_once_with(
            {"revogado": True}, synchronize_session=False
        )
        wa_send_text = self.mocks[6]
        wa_send_text.assert_called_once()
        self.assertEqual(wa_send_text.call_args.args[0], "5511999998888")
        self.assertIn("*Senha temporária:* 12345678901", wa_send_text.call_args.args[1])

    def test_mismatched_phone_does_not_reset_or_send_password(self):
        with flask_app.test_client() as client:
            response = client.post(
                "/api/app/funcionario/auth/esqueci-senha",
                json={"cpf": "12345678901", "telefone": "11911112222"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.funcionario.app_senha, "senha-anterior")
        self.assertFalse(self.funcionario.app_trocar_senha)
        self.revoke_update.assert_not_called()
        self.mocks[6].assert_not_called()


if __name__ == "__main__":
    unittest.main()
