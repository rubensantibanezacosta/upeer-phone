package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge

internal class RegistryEnrollmentAuthorizerAccount(
  private val identity: CryptoIdentity,
  private val device: DeviceIdentity,
  private val registries: AccountRegistryStore,
) : EnrollmentAuthorizerAccount {
  private val signer = CryptoAccountRegistrySigner(identity)

  override val accountUpeerId: String
    get() = identity.upeerID()

  override fun createInvite(ttlMillis: Long, nowMillis: Long): String =
    identity.createEnrollmentInvite(ttlMillis, nowMillis)

  override fun openRequest(inviteJSON: String, requestJSON: String, nowMillis: Long): String =
    identity.openEnrollmentRequest(inviteJSON, requestJSON, nowMillis)

  override fun issueCertificate(inviteJSON: String, requestJSON: String, epoch: Long, issuedAt: Long, ttlMillis: Long): String =
    identity.issueEnrollmentCertificate(inviteJSON, requestJSON, epoch, issuedAt, ttlMillis)

  override fun openCertificate(inviteJSON: String, sealedJSON: String, nowMillis: Long): String =
    Transportbridge.openEnrollmentCertificate(inviteJSON, sealedJSON, accountUpeerId, nowMillis)

  override fun nextEpoch(): Long {
    val status = registries.ensure(accountUpeerId, device.certificateJSON, signer)
    return maxOf(status.epoch + 1, 1)
  }

  override fun registerDevice(deviceCertificateJSON: String): String {
    val snapshot = registries.addDevice(accountUpeerId, device.certificateJSON, deviceCertificateJSON, signer)
    return snapshot.registryJSON
  }
}
