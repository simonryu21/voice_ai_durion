package com.lgcns.codeengine.util

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader


/**
 * @author Kevin Kowalewski
 *
 * Its not 100% identical. The answer I provided contains code
 * to actually attempt rooted commands, that would not prompt
 * a Super User request to the user.
 * Checking for test-keys does not work on all rooted devices (didn't work on mine).
 * Testing for SuperUser.apk is also not perfect,
 * users can uninstall it after gaining root
 *
 * http://arabiannight.tistory.com/64
 */
object SecurityUtils {

    fun checkRooting (context: Context): Boolean {
        var rooted = false
        try {
            // check rooted phone (not 100%)
            rooted = isDeviceRooted()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        val cheated = checkCheatsInstalled(context)
        return rooted or cheated
    }

    private fun checkCheatsInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        val cheats = getCheatApplicationLists()
        for (app in apps) {
            if (cheats.contains(app.packageName)) {
                return true
            }
        }
        return false
    }

    private fun isDeviceRooted(): Boolean {
        if (checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4() || isFridaRunning()) {
            return true
        }

        return false
    }

    private fun checkRootMethod1(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        return false
    }

    private fun checkRootMethod2(): Boolean {
        val arrBins = getHackedBins()
        for (bin in arrBins) {
            val file = File(bin)
            if (file.exists())
                return true
            else
                Timber.d("Not exist: ${file.absolutePath}")
        }

        return false
    }

    private fun checkRootMethod3(): Boolean {
        var rooted = false
        try {
            Runtime.getRuntime().exec("su")
            rooted = true
        } catch (e: Exception) {
        }
        return rooted
    }

    private fun checkRootMethod4(): Boolean {
        return findBinary("su")
    }

    private fun findBinary(binaryName: String): Boolean {
        val arrDirs = getBinaryDirs()
        for (dir in arrDirs) {
            if (File(dir + binaryName).exists())
                return true
        }

        return false
    }

    private fun getHackedBins(): Array<String> {
        return arrayOf(
            "/Applications/blackra1n.app",
            "/Applications/Cydia.app",
            "/Applications/FakeCarrier.app",
            "/Applications/Icy.app",
            "/Applications/IntelliScreen.app",
            "/Applications/MxTube.app",
            "/Applications/RockApp.app",
            "/Applications/SBSettings.app",
            "/Applications/WinterBoard.app",
            "/Library/MobileSubstrate/DynamicLibraries/LiveClock.plist",
            "/Library/MobileSubstrate/DynamicLibraries/Veency.plist",
            "/private/var/lib/apt",
            "/private/var/lib/cydia",
            "/Private/var/mobile/Library/AddressBook/AddressBook.sqlitedb",
            "/private/var/mobile/Library/SBSettings/Themes",
            "/private/var/stash",
            "/private/var/tmp/cydia.log",
            "/System/Library/KeyboardDictionaries",
            "/System/Library/LaunchDaemons/com.ikey.bbot.plist",
            "/System/Library/LaunchDaemons/com.saurik.Cydia.Startup.plist",
            "/usr/bin/sshd",
            "/usr/libexec/sftp-server",
            "/usr/sbin/sshd",
            "/system/app/SuperUser.apk", "/data/data/com.noshufou.android.su",
            "su", "busybox", "supersu", "Superuser.apk", "KingoUser.apk", "SuperSu.apk","magisk",
        )
    }

    private fun getCheatApplicationLists(): Array<String> {
        return arrayOf(
            "cn.luomao.gamekiller",
            "cn.maocai.gameki11er", "cn.maocai.gamekiller", "cn.mc.sq",
            "com.cih.game_cih", "com.cih.gamecih", "com.cih.gamecih2",
            "cris.jeong.samsung", "cris1.jeong1.samsung1",
            "cris2.jeong2.samsung2", "idv.aqua.bulldog", "lg.min.cris",
            "samsung.cris.jeong", "test.aaa.1", "test.bbb.2",
            "com.noshufou.android.su", "com.noshufou.android.su.elite", "eu.chainfire.supersu",
            "com.koushikdutta.superuser", "com.thirdparty.superuser", "com.yellowes.su", "com.koushikdutta.rommanager",
            "com.koushikdutta.rommanager.license", "com.dimonvideo.luckypatcher", "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine", "com.ramdroid.appquarantinepro", "com.devadvance.rootcloak", "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer", "com.saurik.substrate", "com.zachspong.temprootremovejb", "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree", "com.formyhm.hiderootPremium", "com.formyhm.hideroot", "me.phh.superuser",
            "eu.chainfire.supersu.pro", "com.kingouser.com", "com.android.vending.billing.InAppBillingService.COIN","com.topjohnwu.magisk"
        )
    }

    private fun getBinaryDirs() : Array<String> {
        return arrayOf(
            "/sbin/", "/system/bin/", "/system/xbin/", "/data/local/xbin/", "/data/local/bin/",
            "/system/sd/xbin/", "/system/bin/failsafe/", "/data/local/",
        )
    }

    private fun isFridaRunning(): Boolean {
        try {
            // Frida가 주로 주입되는 프로세스의 이름을 확인
            val processList = arrayOf(
                "/system/bin/frida-server",
                "frida"
            )

            // 프로세스 목록 검사
            val processBuilder = ProcessBuilder("/system/bin/ps")
            val process = processBuilder.start()
            process.waitFor()

            // 프로세스 목록 출력
            val inputStream = process.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            while (true) {
                val line: String = reader.readLine() ?: break
                for (processName in processList) {
                    Timber.d("compare $line, $processName")
                    if (line.contains(processName)) {
                        Timber.e("Frida detected!")
                        return true
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        Timber.d("Frida not detected.")
        return false
    }

}


