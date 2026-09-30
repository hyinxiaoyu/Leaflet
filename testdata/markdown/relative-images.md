# Relative images

Local images resolve inside the authorized library tree.
Remote images must render as a static placeholder and never fetch automatically.

![small icon](assets/leaf-16.png)
![larger image](assets/leaf-256.png)
![image with title](assets/leaf-16.png "Leaf icon")
![path with spaces](my%20docs/spaced%20image.png)
![Chinese path](资料/图片.png)
![parent climb](../testdata-anchor.png)
![escaped above root](../../outside-root.png)
![missing file](assets/missing.png)
![missing with title](assets/nope.png "Not here")
![remote https (placeholder, no fetch)](https://example.com/leaf.png)
![remote http (placeholder, no fetch)](http://example.com/leaf.png)
![unsupported type](assets/vector.svg)
![empty source]()
![unknown scheme](foobar:image.png)

Reference-style local image:

![ref icon][leaf-ref]

[leaf-ref]: assets/leaf-16.png
