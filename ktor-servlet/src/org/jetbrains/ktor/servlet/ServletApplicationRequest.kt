package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*
import java.nio.charset.*
import java.util.*
import javax.servlet.*
import javax.servlet.http.*

public class ServletApplicationRequest(override val application: Application,
                                       private val servletRequest: HttpServletRequest,
                                       private val servletResponse: HttpServletResponse) : ApplicationRequest {
    override val requestLine: HttpRequestLine by lazy {
        HttpRequestLine(HttpMethod.parse(servletRequest.method), servletRequest.requestURI, servletRequest.protocol)
    }

    override val body: String
        get() {
            val charsetName = contentType().parameter("charset")
            val charset = charsetName?.let { Charset.forName(it) } ?: Charsets.ISO_8859_1
            return servletRequest.inputStream.reader(charset).readText()
        }

    override val parameters: Map<String, List<String>> by lazy {
        val result = HashMap<String, MutableList<String>>()
        val parametersMap = servletRequest.parameterMap
        if (parametersMap != null) {
            for ((key, values) in parametersMap) {
                if (values != null) {
                    result.getOrPut(key, { arrayListOf() }).addAll(values)
                }
            }
        }
        result
    }

    override val headers: Map<String, String> by lazy {
        // TODO: consider doing the opposite, splitting headers by comma and making it String to List<String> map
        servletRequest.headerNames.asSequence().toMap({ it }, {
            servletRequest.getHeaders(it).asSequence().join(", ")
        })
    }

    private var asyncContext: AsyncContext? = null
    fun continueAsync(asyncContext: AsyncContext) {
        this.asyncContext = asyncContext
    }

    var response: Response? = null
    override val createResponse = Interceptable0<ApplicationResponse> {
        val currentResponse = response
        if (currentResponse != null)
            throw IllegalStateException("There should be only one response for a single request. Make sure you haven't called response more than once.")
        response = Response()
        response!!
    }

    inner class Response : ApplicationResponse {
        override val header = Interceptable2<String, String, ApplicationResponse> { name, value ->
            servletResponse.setHeader(name, value)
            this
        }

        override val status = Interceptable1<Int, ApplicationResponse> { code ->
            servletResponse.status = code
            this
        }

        override val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
            throw UnsupportedOperationException("No known way to stream value $value")
        }

        override val stream = Interceptable1<OutputStream.() -> Unit, ApplicationRequestStatus> { body ->
            //val stream = servletResponse.outputStream
            val stream = ByteArrayOutputStream()
            stream.body()
            stream.flush()

            val content = stream.toString()
            servletResponse.writer.write(content)
            servletResponse.flushBuffer()
            if (asyncContext != null) {
                asyncContext?.complete()
            }
            ApplicationRequestStatus.Handled
        }
    }
}