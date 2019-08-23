(ns showoff.core
  (:require [clojure.core.async :refer [go <! >! >!! <!! chan thread]]))

                                        ;CORE.ASYNC

"Two of my favorite things about clojure are:
   1. Its philosophy of constructing complex structures out of simple parts.

   2. The power of the macro system to make the language closer to \"a tool of thought\"  (Ken Iverson of IBM, a guy you should look up)

Core.async combines these two.



So what's it about?

Basically:  threads, asynchronous processing, and concurrency.

ASYNC

This is the easiest way to show what core.async is about.


Let's think about events on the frontend.  You've probably written something like this:"

#_[:button {:on-click #(do-something!)}]

"What's special about this is that it's not normal executiion flow.  Most of our code is *procedural*---we say what we want done, and the JVM goes line-by line, executing each line as it comes across it, or it's called.

But this is special---the #(do-something) fn is called when the user clicks.


The browser does this for us...but let's look at another example (this from humgrants):"

#_(defn submit! "Submit the SUBMISSION atom as a new form"
  []
  (let [url "/submit"
        method POST
        pmap {:handler (fn [r]
                         (js/alert "Your submission has been received")
                         (rfe/push-state :humgrants.routes/status-route))
              :__anti-forgery-token js/csrfToken
              :error-handler (fn [e] (js/alert "There has been an error"))
              :params {:submission @SUBMISSION}}]
    (method url pmap)))

"This is the code to submit a grant application to the server.  Note the :handler key, where we put a fn that's called once the server responds.

What we WANT to describe here is a sequential process:  talk-to-server -> do-something

But the way we have to write it in practice is by passing the second step in as an ARGUMENT to the first:  (talk-to-server do-something).

This is fine with only two, but if each additional step gets stuffed into the last one, which was stuffed into the one before that, we get what JS programmers call...


CALLBACK HELL"

#_{
fs.readdir(source, function (err, files) {     //<- one fn def
  if (err) {
    console.log('Error finding files: ' + err)
  } else {
    files.forEach(function (filename, fileIndex) {  // <- two fn defs
      console.log(filename)
      gm(source + filename).size(function (err, values) {  // <- three fn defs
        if (err) {
          console.log('Error identifying file size: ' + err)
        } else {
          console.log(filename + ' : ' + values)
          aspect = (values.width / values.height)
          widths.forEach(function (width, widthIndex) { //  <- four fn defs
            height = Math.round(width / aspect)     // we are now four fn levelss deep!
            console.log('resizing ' + filename + 'to ' + height + 'x' + height)
            this.resize(width, height).write(dest + 'w' + width + '_' + filename, function(err) {
              if (err) console.log('Error writing file: ' + err)
            })
          }.bind(this))
        }
      })
    })   // <- they have to do this multiline thing because they are not blessed with paredit like you are.  
  }
})
}

"While the paren thing is trivially tackled with clojure, the define-sequential-behavior-by-a-series-of-concentric-fns-with-fns is not.

What's needed is an entirely different way to think/write about this stuff.

Fortunately, straight-up making things up is what lisp, and by extension clojure, is best at.


CHANNELS

Core.async comes up with a new abstraction: the channel.  A channel is like: a pipe, a queue (not a stack!), a waterslide, a to-do list...you get the idea.  

"

(def our-chan (clojure.core.async/chan))

"There are two ways of dealing with a chan:  blocking and non-blocking.


A blocking take, or a blocking put, will try to write to or read from a chan.  If they cannot---for writes, the chan has no room or no waiting reader, or for reads, the chan has no data and no pending puts---the thread stops, waiting until those conditions are met.

If you only have one thread of execution, then this is very unfortunate, as those conditions will NEVER be met, and your whole program stops forever.


For instance, I nuked my REPL while writing this.
"

(<!! our-chan)


"The traditional way to deal with this is to have more than one thread.."

(thread (>!! our-chan 5))

"This new thread has put a value of 5 onto our-chan.  That thread is now stopped, waiting for a val.  Let's take from it"

(<!! our-chan)

"(oops!)"

(println (<!! our-chan))






(def our-chan (chan))
(go (>! our-chan 4))

(println (range 1000))

(go (<! our-chan))


"The go macro operates at COMPILE time, remember.  It:

 - searches the contained code for channel operations (<!, >!,)

 - runs the code on a new thread (clojure) or \"parks\"

looks for  and stops the thread until "


(let [c (chan)
      bignum 100000000
      processing? (atom true)
      seconds (atom 0)]
  (go
    (println "This thread is about to park, waiting for an answer...")
    (println "This thread has " (<! c)))
  (go (while @processing?
        (Thread/sleep 1000)
        (println (str @seconds " seconds have passed"))
        (swap! seconds inc)))
  (go (>! c (let [result (->> (reduce + (range bignum)) (/ bignum))]
                     (reset! processing? false)
                     result))))

(do
  (def mod-even (fn [n] #(= 0 (mod % n))))
  (def fizz? (mod-even 3))
  (def buzz? (mod-even 5))
  (def fizzbuzz? #(and (fizz? %) (buzz? %))))


(let [fizz-chan (chan)
      buzz-chan (chan)
      fizzbuzz-chan (chan)
      other-chan (chan)]

  (go (doseq [n (range 100)]
        (Thread/sleep 250)
        (cond (fizzbuzz? n)
              (>! fizzbuzz-chan n)
              (fizz? n)
              (>! fizz-chan n)
              (buzz? n)
              (>! buzz-chan n)
              true
              (>! other-chan n))))
  
  (go (while true
        (<! fizz-chan)
        (println "fizz")))
  (go (while true
        (<! buzz-chan)
        (println "buzz")))
  (go (while true
        (<! fizzbuzz-chan)
        (println "fizzbuzz")))
  (go (while true
        (println  (<! other-chan)))))

"We could do this better with..."


(defn find-p [n]
  (cond (fizzbuzz? n) "fizzbuzz"
        (fizz? n) "fizz"
        (buzz? n) "buzz"
        true n))

(let [c chan]
  (go (doseq [n (range 100)]
        (>! chan n)
        (Thread/sleep 250)))
  (go
    (while true
      (println (find-p (<! c))))))


"But core.async is stupid cool about this...."


(let [find-p #(cond %)
      
      mod-even (fn [n] #(= 0 (mod % n)))
      fizz? (mod-even 3)
      buzz? (mod-even 5)
      fizzbuzz? #(and (fizz? %) (buzz? %))]

  (go (doseq [n (range 100)]
        (Thread/sleep 250)
        (cond (fizzbuzz? n)
              (>! fizzbuzz-chan n)
              (fizz? n)
              (>! fizz-chan n)
              (buzz? n)
              (>! buzz-chan n)
              true
              (>! other-chan n))))
  
  (go (while true
        (<! fizz-chan)
        (println "fizz")))
  (go (while true
        (<! buzz-chan)
        (println "buzz")))
  (go (while true
        (<! fizzbuzz-chan)
        (println "fizzbuzz")))
  (go (while true
        (println  (<! other-chan)))))
