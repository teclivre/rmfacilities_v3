package br.com.rmfacilities.funcionarioapp

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Configura o BottomNavigationView com a navegação padrão entre Home / Tarefas (Docs) /
 * Ponto / Mensagens / Perfil. [selectedId] indica qual item deve aparecer selecionado
 * para refletir a tela atual (e evitar re-iniciar a própria activity).
 */
fun Activity.setupAppBottomNav(nav: BottomNavigationView, selectedId: Int) {
    nav.selectedItemId = selectedId
    nav.setOnItemSelectedListener { item ->
        if (item.itemId == selectedId) return@setOnItemSelectedListener true
        val target = when (item.itemId) {
            R.id.nav_home -> HomeActivity::class.java
            R.id.nav_tarefas -> DocumentosActivity::class.java
            R.id.nav_ponto -> PontoActivity::class.java
            R.id.nav_mensagens -> MensagensActivity::class.java
            R.id.nav_perfil -> PerfilActivity::class.java
            else -> return@setOnItemSelectedListener false
        }
        startActivity(
            Intent(this, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
        true
    }
}
