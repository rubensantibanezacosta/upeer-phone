package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge

internal interface AccountRegistrySigner {
  val accountUpeerId: String

  fun build(certificatesJSON: String, revokedJSON: String, epoch: Long, issuedAt: Long, ttlMillis: Long): String

  fun revoke(previousRegistryJSON: String, deviceId: String, reason: String, nowMillis: Long): String

  fun validateAdmission(
    certificateJSON: String,
    registryJSON: String,
    expectedAccountUpeerId: String,
    nowMillis: Long,
  ): Boolean
}

internal class CryptoAccountRegistrySigner(private val identity: CryptoIdentity) : AccountRegistrySigner {
  override val accountUpeerId: String
    get() = identity.upeerID()

  override fun build(certificatesJSON: String, revokedJSON: String, epoch: Long, issuedAt: Long, ttlMillis: Long): String =
    identity.buildAccountRegistryFromCertificates(certificatesJSON, revokedJSON, epoch, issuedAt, ttlMillis)

  override fun revoke(previousRegistryJSON: String, deviceId: String, reason: String, nowMillis: Long): String =
    identity.revokeAccountDevice(previousRegistryJSON, deviceId, reason, nowMillis)

  override fun validateAdmission(
    certificateJSON: String,
    registryJSON: String,
    expectedAccountUpeerId: String,
    nowMillis: Long,
  ): Boolean = Transportbridge.validateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerId, nowMillis)
}
