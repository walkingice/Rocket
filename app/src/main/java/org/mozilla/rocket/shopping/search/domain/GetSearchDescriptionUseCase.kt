package org.mozilla.rocket.shopping.search.domain

import org.mozilla.rocket.shopping.search.data.ShoppingSearchRepository

class GetSearchDescriptionUseCase(val repository: ShoppingSearchRepository) {
    operator fun invoke(): String {
        return repository.getSearchDescription()
    }
}
