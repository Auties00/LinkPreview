# Link Preview Java

### What is link preview

This library generates a preview for a link or for a text containing any number of links. 
Its functionality is aligned with [link-preview-js](https://github.com/ospfranco/link-preview-js) 
as I was looking for an alternative to this library for Java but couldn't find any.

### How to install

#### Maven

```xml
<dependency>
    <groupId>com.github.auties00</groupId>
    <artifactId>link-preview</artifactId>
    <version>2.4</version>
</dependency>
```

#### Gradle

1. Groovy DSL
   ```groovy
   implementation 'com.github.auties00:link-preview:2.4'
   ```

2. Kotlin DSL
   ```kotlin
   implementation("com.github.auties00:link-preview:2.4")
   ```
   
### How to use

1. Text
   ```java
   LinkPreview.createPreviews("This is a string containing links: google.com");
   ```
   
2. URI
   ```java
   LinkPreview.createPreviews(URI.create("https://google.com/"));
   ```