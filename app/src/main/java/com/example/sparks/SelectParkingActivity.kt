package com.example.sparks

import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doAfterTextChanged
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.navigation.NavigationView
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import kotlinx.android.synthetic.main.activity_parking.*
import kotlinx.android.synthetic.main.dialog_logs.view.*
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

class SelectParkingActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    override fun onNavigationItemSelected(p0: MenuItem): Boolean {
        when (p0.itemId) {
            R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))

            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))

            R.id.nav_home -> startActivity(Intent(this, MainActivity::class.java))

            R.id.user_manual -> {
                val alert= AlertDialog.Builder(this)
                alert.setTitle("Uputstvo za upotrebu")
                alert.setMessage("1.Kao korisnik ove aplikacije potrebno je prvo da odaberete destinaciju, kako bi vam aplikacija mogla prikazati informacije o parking mjestima u krugu od 500 " +
                        "2.odaberite parking, potom je potebno da unesete registarske tablice, vrijeme na koje se parking placa \n" +
                        "3.nakon toga pritisnite posalji")
                alert.setPositiveButton("OK"){ dialog, _ ->
                    dialog.dismiss()
                }
                alert.show()
            }
            R.id.report_error ->{
                val builder = AlertDialog.Builder(this)
                val inflater = layoutInflater
                builder.setTitle("Ako ste primjetili ikakve greške u radu aplikacije, molimo vas da ih ukratko opišete")
                val dialogLayout = inflater.inflate(R.layout.error_dialog, null)
                builder.setView(dialogLayout)
                builder.setPositiveButton("OK"){ dialog, _ ->
                    dialog.dismiss()
                }
                builder.show()
            }

            R.id.about -> {
                val alert= AlertDialog.Builder(this)
                alert.setTitle("O aplikaciji")
                alert.setMessage("Aplikacija SPARK je rezultat urađenog projekta iz predmeta Projektovanje Softvera, omogućava korisniku da lakše pronađe slobodno parking mjesto kao i da mu prikaže mnoštvo korisnih informacija o parkinzima u Banja Luci \n \nVerzija:1.0")
                alert.setPositiveButton("OK"){ dialog, _ ->
                    dialog.dismiss()
                }
                alert.show()
            }

            R.id.nav_logs -> {
                val logsLayout = layoutInflater.inflate(R.layout.dialog_logs, null)
                val logsDialog = AlertDialog.Builder(this)
                logsDialog.setView(logsLayout)
                logsDialog.setTitle("Logovi")
                logsLayout.recycler_view.layoutManager = LinearLayoutManager(this)
                logsLayout.recycler_view.adapter= LogDataAdapter(this, Supplier.logData)
                logsDialog.setPositiveButton("Ok"){ dialog, _ ->dialog.dismiss() }
                logsDialog.show()
                return false
            }
        }
        return true
    }

    private var pastOverlay: MapOverlay? = null
    private var  lastPos: MapMarker?= null      //trenutna desitnacija parkinga
    private var currPos: GeoCoordinate? = null
    private var currRoute: MapRoute? = null
    private var map: Map? = null
    private lateinit var router: CoreRouter
    private var mapFragment: AndroidXMapFragment? = null

    companion object {
        var posManager: PositioningManager? = null
        var smsNumber: String? = null
        var plates: String? = null
        var length: Long? = null
    }

    private var periodSelected: Boolean? = null
    private var destinationSelected: Boolean? = true
    private var platesSelected: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parking)
        val toolbar = findViewById<Toolbar>(R.id.drawer_toolbar)
        setSupportActionBar(toolbar)
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        val naviationView: NavigationView = findViewById(R.id.nav_view_drawer)
        naviationView.setNavigationItemSelectedListener(this)
        val toggle = ActionBarDrawerToggle(this, drawer, toolbar,
            R.string.navigation_bar_open, R.string.navigation_bar_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        routeParking.isClickable = false

        smsNumber = "+387" + PreferenceManager
            .getDefaultSharedPreferences(this)
            .getString("name", null)!!.substring(1)

        periodTextView.setOnClickListener{
            val builder = AlertDialog.Builder(this)
            val inflater = layoutInflater
            builder.setTitle("Izaberite period rezervacije")
            val dialogLayout = inflater.inflate(R.layout.dialog_reserve, null)

            dialogLayout.findViewById<RadioGroup>(R.id.period_radio_group)
                .setOnCheckedChangeListener{ _: RadioGroup, i: Int ->
                    when(i){
                        R.id.extend1 -> {
                            length = 5*60*1000
                        }

                        R.id.extend2 -> {
                            length = 10*60*1000
                        }

                        R.id.extend3 -> {
                            length = 15*60*1000
                        }

                        R.id.extend4 -> {
                            length = 20*60*1000
                        }

                        R.id.extend5 -> {
                            length = 30*60*1000
                        }
                    }

                    if(i != -1){
                        periodSelected = true
                        routeParking.isClickable = periodSelected!! && platesSelected!! && destinationSelected!!
                    }
                }

            builder.setView(dialogLayout)
            builder.setPositiveButton("Ok"){ dialog, _ ->
                dialog.dismiss()
            }
            builder.show()
        }
        routeParking.setOnClickListener {
            WorkManager
                .getInstance(applicationContext)
                .enqueue(
                    PeriodicWorkRequest
                    .Builder(CheckArrivalWorker::class.java, 5, TimeUnit.SECONDS)
                    .build())
        }

        initialize()

        registryNumberEditText.doAfterTextChanged {
            platesSelected = !it.isNullOrBlank()
            routeParking.isClickable = periodSelected!! && platesSelected!! && destinationSelected!!

            if(platesSelected!!)
                plates = it.toString()
        }

        map!!.addMapObjects(PSpotSupplier.parkingSports.map { ps -> ps.getMarker() })

        PSpotSupplier.addMap(map!!)
    }

    /*
    * Metoda za prikazijvanje rute
    * na event kada se izracuna nova ruta, izbrise staru i prikaze novu
    *
    * */

    private fun routeListenerFactory(): CoreRouter.Listener{
        return object: CoreRouter.Listener{
            override fun onCalculateRouteFinished(p0: MutableList<RouteResult>?, p1: RoutingError) {
                if(p1 == RoutingError.NONE){

                    if(currRoute != null)
                        map!!.removeMapObject(currRoute!!)

                    currRoute = MapRoute(p0!![0].route)

                    map!!.addMapObject(currRoute!!)
                }
            }

            override fun onProgress(p0: Int) {}

        }

    }

    /*
    *
    * Metoda za manipulaciju markerima
    * kada se klikne na novu poziciju na mapi, brise se stara i racuna nova
    *
    * */

    private fun markerListenerFactory(): MapGesture.OnGestureListener{
        return object: MapGesture.OnGestureListener{

            override fun onLongPressRelease() {}

            override fun onRotateEvent(p0: Float): Boolean {
                return false
            }

            override fun onMultiFingerManipulationStart() {}

            override fun onPinchLocked() {}

            override fun onPinchZoomEvent(p0: Float, p1: PointF): Boolean {
                return false
            }

            override fun onTapEvent(p0: PointF): Boolean {
                if(pastOverlay != null)
                    map!!.removeMapOverlay(pastOverlay!!)

                pastOverlay = null

                return true
            }

            override fun onPanStart() {}

            override fun onMultiFingerManipulationEnd() {}

            /*
            *
            * Na dvoklik se postavlja marker, jer
            * na jedan klik na marker prikazuje
            * se infobubble o njemu
            *
            * */

            override fun onDoubleTapEvent(p0: PointF): Boolean {
                if(lastPos != null)
                    map!!.removeMapObject(lastPos!!)

                val image = Image()
                image.setImageResource(R.drawable.parking_pin_large)
                lastPos = MapMarker(map!!.pixelToGeo(p0)!!, image)
                map?.addMapObject(lastPos!!)

                ShowPreferableSpotsTask(map!!, lastPos!!.coordinate)

                return true
            }

            override fun onPanEnd() {}

            override fun onTiltEvent(p0: Float): Boolean {
                return false
            }

            override fun onMapObjectsSelected(p0: MutableList<ViewObject>): Boolean {
                for(viewObject in p0){
                    if((viewObject as MapObject).type == MapObject.Type.MARKER && (viewObject as MapMarker) != lastPos){
                        if(pastOverlay != null)
                            map!!.removeMapOverlay(pastOverlay!!)

                        val spot = PSpotSupplier.parkingSports.filter { spot -> spot.getMarker() == viewObject }[0]
                        val view = layoutInflater.inflate(R.layout.infobubble, null)

                        val  icon = view.findViewById<ImageView>(R.id.icon_parking)
                        icon.setImageResource(viewObject.description!!.toInt())

                        val ocupation = view.findViewById<TextView>(R.id.tv_ocupation)
                        val placeholder = spot.freeSpace.toString() + "/" + spot.space.toString()
                        ocupation.text = placeholder

                        val name = view.findViewById<TextView>(R.id.tv_name)
                        name.text = spot.name

                        val zone = view.findViewById<TextView>(R.id.tv_zone)
                        zone.text = "1"

                        val button = view.findViewById<Button>(R.id.tv_select)

                        button.setOnClickListener{
                            val tmpMarker = viewObject
                            if(MainActivity.DESTINATION != null)
                                PSpotSupplier.parkingSports
                                    .filter { spot -> spot.getMarker() == MainActivity.DESTINATION }[0]
                                    .shrinkMarker()
                            MainActivity.DESTINATION = tmpMarker

                            PSpotSupplier.parkingSports
                                .filter { spot -> spot.getMarker() == MainActivity.DESTINATION }[0]
                                .expandMarker()

                            routeParking.isClickable = periodSelected!! && platesSelected!! && destinationSelected!!
                        }
                        val overlay = MapOverlay(view, viewObject.coordinate)

                        map!!.addMapOverlay(overlay)

                        pastOverlay = overlay

                        break
                    }
                }
                return true
            }

            override fun onRotateLocked() {}

            override fun onLongPressEvent(p0: PointF): Boolean {
                return false
            }

            override fun onTwoFingerTapEvent(p0: PointF): Boolean {
                return false
            }
        }

    }

    /*
    * Metoda za racunanje rute
    * kada se izabere nova konacna destiniacija
    * metoda kreira novi route objekat
    *
    *
    * */

    private fun showRoute() {
        if(lastPos != null){
            val coordinate: GeoCoordinate = lastPos!!.coordinate

            val routePlan = RoutePlan()

            routePlan.addWaypoint(RouteWaypoint(currPos!!))

            routePlan.addWaypoint(RouteWaypoint(coordinate))

            val routeOptions = RouteOptions()

            routeOptions.transportMode = RouteOptions.TransportMode.CAR
            routeOptions.routeType = RouteOptions.Type.BALANCED

            routePlan.routeOptions = routeOptions

            router.calculateRoute(routePlan, routeListenerFactory())
        }
    }


    /*
    *
    * Metoda za inicijalizaciju mape
    * pokrece GPS servis i na event promjene pozicije uredjaja vrsi se update pozicije na mapi
    *
    * */
    private fun initialize() {

        mapFragment = supportFragmentManager.findFragmentById(R.id.map_view_main) as AndroidXMapFragment?

        val success:Boolean = MapSettings.setIsolatedDiskCacheRootPath(
            applicationContext.getExternalFilesDir(null)!!.absolutePath + File.separator + ".here-maps"
        )

        if(!success)
            Toast.makeText(applicationContext, "Unable to set isolated disk cache path",
                Toast.LENGTH_LONG).show()
       // else{
            mapFragment?.init{
                if(it == OnEngineInitListener.Error.NONE){
                    map = mapFragment!!.map
                    map!!.setCenter(GeoCoordinate(49.196261, -123.004773, 0.0), Map.Animation.NONE)
                    posManager = PositioningManager.getInstance()
                    posManager!!.start(PositioningManager.LocationMethod.GPS_NETWORK)

                    // Define positioning listener
                    // Define positioning listener

                    val positionListener: PositioningManager.OnPositionChangedListener =
                        object : PositioningManager.OnPositionChangedListener {
                            override fun onPositionUpdated(
                                method: PositioningManager.LocationMethod,
                                position: GeoPosition?, isMapMatched: Boolean
                            ) {
                                if (position != null) {
                                    map!!.setCenter(
                                        position.coordinate,
                                        Map.Animation.NONE

                                    )

                                    mapFragment!!.positionIndicator!!.isVisible = true

                                    Toast.makeText(applicationContext, "Udaljenost: " + String.format(
                                        Locale.US, "%.6f", position.coordinate.distanceTo(lastPos!!.coordinate))
                                        , Toast.LENGTH_LONG).show()
                                }

                                currPos = position!!.coordinate



                            }

                            override fun onPositionFixChanged(
                                method: PositioningManager.LocationMethod,
                                status: PositioningManager.LocationStatus
                            ) {
                            }
                        }

                    // Register positioning listener

                    posManager!!.addListener(
                        WeakReference(positionListener)
                    )
                    map!!.zoomLevel = (map!!.maxZoomLevel + map!!.minZoomLevel) / 2


                    mapFragment!!.mapGesture!!.addOnGestureListener(markerListenerFactory(), 1, false)

                    router = CoreRouter()

                } else
                    Toast.makeText(applicationContext, "Cannot Initialize Map Fragment" + it.details,
                        Toast.LENGTH_LONG).show()

            }
        //}


    }

}