package com.example.core.enrollment

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PairingPayload(
    val pairingToken: String,
    val pairingCode: String = "849210",
    val parentId: String,
    val parentName: String = "Parent's Phone",
    val childDeviceId: String,
    val childDeviceName: String,
    val expiresAtEpochMs: Long,
    val signature: String
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis > expiresAtEpochMs
    }

    fun formattedPairingCode(): String {
        val clean = pairingCode.filter { it.isDigit() }
        return if (clean.length == 6) "${clean.take(3)}-${clean.takeLast(3)}" else pairingCode
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("token", pairingToken)
        json.put("code", pairingCode)
        json.put("parentId", parentId)
        json.put("parentName", parentName)
        json.put("deviceId", childDeviceId)
        json.put("deviceName", childDeviceName)
        json.put("expiresAt", expiresAtEpochMs)
        json.put("sig", signature)
        return json.toString()
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"

        fun create(
            parentId: String,
            childDeviceName: String,
            parentName: String = "Parent's Phone",
            validityMinutes: Long = 15,
            parentSecret: String = "parent_secret_key"
        ): PairingPayload {
            val token = UUID.randomUUID().toString()
            val code = "%06d".format((100000..999999).random())
            val deviceId = "child_${UUID.randomUUID().toString().take(8)}"
            val expiresAt = System.currentTimeMillis() + (validityMinutes * 60_000L)
            val dataToSign = "$token:$parentId:$deviceId:$expiresAt"
            val signature = computeHmac(dataToSign, parentSecret)

            return PairingPayload(
                pairingToken = token,
                pairingCode = code,
                parentId = parentId,
                parentName = parentName,
                childDeviceId = deviceId,
                childDeviceName = childDeviceName,
                expiresAtEpochMs = expiresAt,
                signature = signature
            )
        }

        fun fromJson(jsonString: String): PairingPayload? {
            return try {
                val json = JSONObject(jsonString)
                val token = json.getString("token")
                val code = if (json.has("code")) json.getString("code") else token.filter { it.isDigit() }.take(6).padStart(6, '7')
                val parentName = if (json.has("parentName")) json.getString("parentName") else "Parent's Phone"
                PairingPayload(
                    pairingToken = token,
                    pairingCode = code,
                    parentId = json.getString("parentId"),
                    parentName = parentName,
                    childDeviceId = json.getString("deviceId"),
                    childDeviceName = json.getString("deviceName"),
                    expiresAtEpochMs = json.getLong("expiresAt"),
                    signature = json.getString("sig")
                )
            } catch (e: Exception) {
                null
            }
        }

        fun verifySignature(
            payload: PairingPayload,
            parentSecret: String = "parent_secret_key"
        ): Boolean {
            val dataToSign = "${payload.pairingToken}:${payload.parentId}:${payload.childDeviceId}:${payload.expiresAtEpochMs}"
            val expectedSig = computeHmac(dataToSign, parentSecret)
            return MessageDigest.isEqual(expectedSig.toByteArray(), payload.signature.toByteArray())
        }

        private fun computeHmac(data: String, secret: String): String {
            val key = SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM)
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(key)
            val rawHmac = mac.doFinal(data.toByteArray())
            return rawHmac.joinToString("") { "%02x".format(it) }
        }
    }
}
