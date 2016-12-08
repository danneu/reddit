
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

