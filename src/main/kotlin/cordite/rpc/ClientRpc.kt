package cordite.rpc

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor

abstract class ClientRpc(address: String, username: String, password: String) {
    private val rpcConnection: CordaRPCConnection
    protected val rpcOps: CordaRPCOps
    protected val party: Party

    companion object {
        private val logger = loggerFor<ClientRpc>()
    }

    init {
        val client = CordaRPCClient(NetworkHostAndPort.parse(address))
        rpcConnection = client.start(username, password)
        rpcOps = rpcConnection.proxy
        party = rpcOps.nodeInfo().legalIdentities.single()
    }

    fun close() {
        rpcConnection.notifyServerAndClose()
    }
}