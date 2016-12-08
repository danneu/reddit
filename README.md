
# reddit-iterator

A Reddit crawler implemented in Kotlin for consuming submissions and comments with a flattened iterator.

The crawler's methods return `Iterator<Submission>` or `Iterator<Comment>`.

Useful for consuming the entire set of submissions and comments for a subreddit.

Internally, the iterator lazily unrolls pagination including "Continue Thread" and "Load More" nodes in comment trees
as you iterate.

By default, an `ApiClient` instance never sends more than one request per second to comply
with Reddit's API requirements.

## Install

TODO

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

    client.submissionsOf("futurology").forEach { submission ->
        client.commentsOf(submission).forEach { comment ->
            println(comment)
        }
    }
}
```

Or:

``` kotlin
import com.danneu.reddit.ApiClient

fun main(args: Array<String>) {
    val client = ApiClient()

    client.commentsOf("futurology").forEach { submission ->
        println(comment)
    }
}
```

### Get a new ApiClient based on an existing one

`ApiClient#fork()` returns a new ApiClient with the same configuration as the original one. Pass in a builder
lambda to customize the copy.

``` kotlin
import com.danneu.reddit.ApiClient
import java.net.Proxy
import java.time.Duration
import java.net.InetSocketAddress

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
