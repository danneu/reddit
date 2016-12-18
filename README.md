
# reddit-iterator [![Jitpack](https://jitpack.io/v/com.danneu/reddit.svg)] (https://jitpack.io/#com.danneu/reddit) [![Build Status](https://travis-ci.org/danneu/reddit-iterator.svg?branch=master)](https://travis-ci.org/danneu/reddit-iterator)

A Reddit crawler implemented in Kotlin for consuming submissions and comments with a flattened iterator.

The crawler's methods return `Iterator<Submission>` or `Iterator<Comment>`.

Useful for consuming the entire set of submissions and comments for a subreddit.

Internally, the iterator lazily unrolls pagination including "Continue Thread" and "Load More" nodes in comment trees
as you iterate.

By default, an `ApiClient` instance never sends more than one request per second to comply
with Reddit's API requirements.

## Install

``` groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    // Always get latest:
    compile "com.danneu:reddit:master-SNAPSHOT"
    // Or get a specific release:
    compile "com.danneu:reddit:0.0.1"
}
```

## Usage

### Crawl latest comments across all subreddits

The server only responds with the latest 1,000 comments, but since you hit CDN cache, this endpoint will paginate
10,000+ comments.

Though due to the cache, the farther you paginate, the more sparse the comments become. For instance, fewer
comments per hour.

``` kotlin
import com.danneu.reddit.ApiClient

fun main(args: Array<String>) {
    val client = ApiClient()

    client.recentComments().forEach { comment ->
        println(comment)
    }
}
```

However, if you run this 24/7 in a loop, you can consume all Reddit comments as they are published.

You'll need to use the following subreddit-scoped methods to consume historical comments.

### Crawl all submissions in a subreddit

``` kotlin
import com.danneu.reddit.ApiClient

fun main(args: Array<String>) {
    val client = ApiClient()

    client.submissionsOf("futurology").forEach { submission ->
        println(submission)
    }
}
```

### Crawl all comments in a subreddit

Iterates comments depth-first.

``` kotlin
import com.danneu.reddit.ApiClient

fun main(args: Array<String>) {
    val client = ApiClient()

    client.commentsOf("futurology").forEach { comment ->
        println(comment)
    }
}
```

Or:

``` kotlin
import com.danneu.reddit.ApiClient

fun main(args: Array<String>) {
    val client = ApiClient()

    client.submissionsOf("futurology").forEach { submission ->
        println("Now crawling: ${submission.url()}")
        client.commentsOf(submission).forEach { comment ->
            println(comment)
        }
    }
}
```

### Get a new ApiClient based on an existing one

`ApiClient#fork()` returns a new ApiClient with the same configuration as the original one. Pass in a builder
lambda to customize the copy.

``` kotlin
import com.danneu.reddit.ApiClient
import java.net.InetSocketAddress
import java.time.Duration
import java.net.Proxy

fun main(args: Array<String>) {
    val client1 = ApiClient {
        proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("1.2.3.4", 8080))
    }

    val client2 = client1.fork {
        throttle = Duration.ofSeconds(2)
    }
}
```

`client2` uses the same proxy as `client1`, but it waits two seconds before each request instead of the
default one second.

## Examples

### Extract all absolute URLs from submissions and comments

``` kotlin
import com.danneu.reddit.ApiClient
import com.danneu.reddit.HasContent
import java.net.URI

// Extend Submissions and Comments with a method that returns only the absolute URLs
fun HasContent.absoluteUrls(): List<URI> = urls().filter { it.isAbsolute }

fun processUrl(uri: URI) = println("found absolute url: ${uri}")

fun main(args: Array<String>) {
    val client = ApiClient()

    client.submissionsOf("futurology").forEach { submission ->
        // Process URLs in the submission body
        submission.absoluteUrls().forEach(::processUrl)

        client.commentsOf(submission).forEach { comment ->
            // Process URLs in each comment body
            comment.absoluteUrls().forEach(::processUrl)
        }
    }
}
```

Even better, in Kotlin, we can rewrite the previous example with a client extension `#urlsOf(subreddit)` that returns
a lazy sequence of urls found in the submission and comment bodies.

``` kotlin
import com.danneu.reddit.ApiClient
import java.net.URI

fun ApiClient.urlsOf(subreddit: String): Sequence<URI> {
    return submissionsOf(subreddit).asSequence().flatMap { submission ->
        submission.urls().asSequence().plus(
            commentsOf(submission).asSequence().flatMap { comment ->
                comment.urls().asSequence()
            }
        )
    }
}

fun main(args: Array<String>) {
    val client = ApiClient()

    client.urlsOf("futurology").filter { it.isAbsolute }.forEach { url ->
        println("found absolute url: $url")
    }
}
```
