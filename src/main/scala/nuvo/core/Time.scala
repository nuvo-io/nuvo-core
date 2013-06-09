package nuvo.core


object Time {
  def now = new Time(System.currentTimeMillis())
}

object Duration {
  def infinite = new Duration(0x7fffffffffffffffL)
  def zero = new Duration(0L)
}

class Time(val t: Long) extends AnyVal with Ordered[Time] {

  def nsec = new Time((t/1E6).toLong)
  def usec = new Time((t/1E3).toLong)
  def msec = this
  def sec = new Time((t*1E3).toLong)

  def + (that: Duration) = new Time(this.t + that.duration)

  def - (that: Time): Duration = {
    require (this.t > that.t)
    new Duration(this.t - that.t)
  }
  def compare(that: Time): Int = (this.t - that.t).toInt
}

class Duration(val duration: Long) extends AnyVal with Ordered[Duration] {

  def nsec = new Duration((duration/1E6).toLong)
  def usec = new Duration((duration/1E3).toLong)
  def msec = this
  def sec = new Duration((duration*1E3).toLong)

  def + (that: Duration) = new Duration(duration + that.duration)

  def - (that: Duration) = {
    require (this.duration > that.duration)
    new Duration(duration + that.duration)
  }

  def / (scale: Int) = {
    require(scale != 0)
    new Duration(this.duration/scale)
  }

  def compare(that: Duration): Int = (this.duration - that.duration).toInt
}
