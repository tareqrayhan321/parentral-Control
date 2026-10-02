package com.example.core.enrollment

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PairingPayload(
    val pairingToken: String,
    val parentId: String,
    val childDeviceId: String,
    val childDeviceName: String,
    val expiresAtEpochMs: Long,
    val signature: String
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis > expiresAtEpochMs
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("token", pairingToken)
        json.put("parentId", parentId)
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
            validityMinutes: Long = 15,
            parentSecret: String = "parent_secret_key"
        ): PairingPayload {
            val token = UUID.randomUUID().toString()
            val deviceId = "child_${UUID.randomUUID().toString().take(8)}"
            val expiresAt = System.currentTimeMillis() + (validityMinutes * 60_000L)
            val dataToSign = "$token:$parentId:$deviceId:$expiresAt"
            val signature = computeHmac(dataToSign, parentSecret)

            return PairingPayload(
                pairingToken = token,
                parentId = parentId,
                childDeviceId = deviceId,
                childDeviceName = childDeviceName,
                expiresAtEpochMs = expiresAt,
                signature = signature
            )
        }

        fun fromJson(jsonString: String): PairingPayload? {
            return try {
                val json = JSONObject(jsonString)
                PairingPayload(
                    pairingToken = json.getString("token"),
                    parentId = json.getString("parentId"),
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
