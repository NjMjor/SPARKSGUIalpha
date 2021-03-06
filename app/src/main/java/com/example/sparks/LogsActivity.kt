package com.example.sparks

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView


class LogsActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    override fun onNavigationItemSelected(p0: MenuItem): Boolean {
        if(p0.itemId != itemId) {
            when (p0.itemId) {
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))

                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))

                R.id.nav_logs -> startActivity(Intent(this, LogsActivity::class.java))

                R.id.nav_home -> startActivity(Intent(this, MainActivity::class.java))

                R.id.user_manual -> {
                    val alert= AlertDialog.Builder(this)
                    alert.setTitle("Uputstvo za upotrebu")
                    alert.setMessage("1.Kao korisnik ove aplikacije potrebno je prvo da odaberete destinaciju, kako bi vam aplikacija mogla prikazati informacije o parking mjestima u krugu od 500 " +
                            "2.odaberite parking, potom je potebno da unesete registarske tablice, vrijeme na koje se parking placa \n" +
                            "3.nakon toga pritisnite posalji")
                    alert.setPositiveButton("OK"){dialog, which ->
                        dialog.dismiss()
                    }
                    alert.show()
                }
                R.id.report_error ->{
                    val builder = AlertDialog.Builder(this)
                    val inflater = layoutInflater
                    builder.setTitle("Ako ste primjetili ikakve greške u radu aplikacije, molimo vas da ih ukratko opišete")
                    val dialogLayout = inflater.inflate(R.layout.error_dialog, null)
                    val editText  = dialogLayout.findViewById<EditText>(R.id.editText)
                    builder.setView(dialogLayout)
                    builder.setPositiveButton("OK"){dialog, which ->
                        dialog.dismiss()
                    }
                    builder.show()
                }

                R.id.about -> {
                    var alert=AlertDialog.Builder(this)
                    alert.setTitle("O aplikaciji")
                    alert.setMessage("Aplikacija SPARK je rezultat urađenog projekta iz predmeta Projektovanje Softvera, omogućava korisniku da lakše pronađe slobodno parking mjesto kao i da mu prikaže mnoštvo korisnih informacija o parkinzima u Banja Luci \n \nVerzija:1.0")
                    alert.setPositiveButton("OK"){dialog,which->
                        dialog.dismiss()
                    }
                    alert.show()
                }
            }
            return true
        } else
            return false

    }

    var isOpen = false
    var Marko=false

    private lateinit var drawer: DrawerLayout
    private val itemId = R.id.nav_logs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var toolbar = findViewById<Toolbar>(R.id.drawer_toolbar)
        setSupportActionBar(toolbar)

        drawer = findViewById(R.id.drawer_layout)

        var naviationView: NavigationView = findViewById(R.id.nav_view_drawer)

        naviationView.setNavigationItemSelectedListener(this)

        var toggle: ActionBarDrawerToggle = ActionBarDrawerToggle(this, drawer, toolbar,
            R.string.navigation_bar_open, R.string.navigation_bar_close)

        drawer.addDrawerListener(toggle)

        toggle.syncState()

    }

    override fun onBackPressed() {
        if(drawer.isDrawerOpen(GravityCompat.START)){
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
