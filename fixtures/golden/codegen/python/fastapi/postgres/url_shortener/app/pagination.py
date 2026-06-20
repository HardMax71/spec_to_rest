PAGE_LIMIT_DEFAULT = 50
PAGE_LIMIT_MIN = 1
PAGE_LIMIT_MAX = 100
PAGE_OFFSET_DEFAULT = 0


class Pagination:
    def __init__(
        self,
        limit: int = PAGE_LIMIT_DEFAULT,
        offset: int = PAGE_OFFSET_DEFAULT,
    ) -> None:
        self.limit = max(PAGE_LIMIT_MIN, min(limit, PAGE_LIMIT_MAX))
        self.offset = max(0, offset)
