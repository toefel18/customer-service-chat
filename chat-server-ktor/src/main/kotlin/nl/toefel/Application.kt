package nl.toefel

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.put
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.SessionStorageMemory
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket

fun main(args: Array<String>) {
    val server = embeddedServer(Netty, port = 8080, module = Application::module)
    server.start(wait = true)
}

data class User(val name: String?)

val chatHistory = mutableMapOf<String, MutableList<String>>()

fun Application.module() {
    install(Sessions) {
        cookie<User>("CHAT_SESSION", SessionStorageMemory()) {
            cookie.path = "/"
        }
    }
    install(CORS) {
        anyHost()
        method(HttpMethod.Put)
        method(HttpMethod.Get)
        allowCredentials = true
    }
    install(CallLogging)
    install(WebSockets)
    install(Routing) {
        get("/user") {
            val user = call.sessions.get<User>() ?: User("guest")
            println(user)
            call.respondText(user.name ?: "uknown", ContentType.Text.Html)
        }

        put("/user/{name}") {
            call.sessions.set(User(name = call.parameters["name"]))
            chatHistory.putIfAbsent(call.parameters["name"]!!, mutableListOf())
            println("Configured chat history " + call.parameters["name"])
            call.respondText("session name set to ${call.sessions.get<User>()?.name}", ContentType.Text.Html)
        }

        webSocket("/chat") {
            // websocketSession
            val user = call.sessions.get<User>() ?: User("guest")

            println("history " + chatHistory[user.name])
            if (chatHistory[user.name]?.isNullOrEmpty() == false) {
                chatHistory[user.name]!!.forEach { outgoing.send(Frame.Text(it)) }
            }
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        chatHistory[user.name]!!.add("${user.name}: $text")
                        outgoing.send(Frame.Text("${user.name}: $text"))
                        if (text.equals("bye", ignoreCase = true)) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
                        }
                    }
                }
            }
        }
    }
}
