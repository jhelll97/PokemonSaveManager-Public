package com.example.pokemonsavemanager

/** Keep event-screen read/validation/render exceptions on a recoverable UI path. */
object EventScreenBoundary {
    fun open(render:()->Unit,failed:(Exception)->Unit){
        try{render()}catch(error:Exception){failed(error)}
    }

    fun diagnostic(error:Exception):String = buildString {
        append(error.javaClass.simpleName)
        error.message?.takeIf{it.isNotBlank()}?.let{append(": ");append(it)}
        error.stackTrace.filter{it.className.startsWith("com.example.pokemonsavemanager")}
            .take(5).forEach{append("\n");append(it.toString())}
    }
}
